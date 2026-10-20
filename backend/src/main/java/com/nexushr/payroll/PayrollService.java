package com.nexushr.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexushr.auth.UserAccount;
import com.nexushr.auth.UserRepository;
import com.nexushr.employee.Employee;
import com.nexushr.employee.EmployeeRepository;
import com.nexushr.employee.EmploymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayrollService {
  private final PayrollStructureRepository structures;
  private final PayrollRunRepository runs;
  private final PayslipRepository payslips;
  private final PayslipCorrectionRepository corrections;
  private final EmployeeRepository employees;
  private final UserRepository users;
  private final EntityManager entityManager;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  public PayrollService(PayrollStructureRepository structures, PayrollRunRepository runs, PayslipRepository payslips,
      PayslipCorrectionRepository corrections, EmployeeRepository employees, UserRepository users,
      EntityManager entityManager, ObjectMapper json, ApplicationEventPublisher events) {
    this.structures = structures;
    this.runs = runs;
    this.payslips = payslips;
    this.corrections = corrections;
    this.employees = employees;
    this.users = users;
    this.entityManager = entityManager;
    this.json = json;
    this.events = events;
  }

  @Transactional
  public PayrollDtos.StructureResponse setStructure(UUID employeeId, PayrollDtos.StructureRequest request) {
    Employee employee = employees.findById(employeeId).orElseThrow(() -> notFound("Employee not found"));
    if (employee.getStatus() != EmploymentStatus.ACTIVE) conflict("Salary structure can only be assigned to an active employee");
    PayrollStructure structure = structures.findByEmployeeId(employeeId).orElse(null);
    if (structure == null) structure = new PayrollStructure(employee, request);
    else structure.update(request);
    return PayrollDtos.StructureResponse.from(structures.saveAndFlush(structure));
  }

  @Transactional(readOnly = true)
  public PayrollDtos.StructureResponse structure(UUID employeeId) {
    PayrollStructure structure = structures.findByEmployeeId(employeeId)
        .orElseThrow(() -> notFound("Salary structure not found"));
    return PayrollDtos.StructureResponse.from(structure);
  }

  @Transactional(readOnly = true)
  public List<PayrollDtos.StructureResponse> structures() {
    return structures.findAll().stream().map(PayrollDtos.StructureResponse::from).toList();
  }

  @Transactional
  public PayrollDtos.RunResponse generate(YearMonth month, String actor) {
    if (month.isAfter(YearMonth.now())) badRequest("A payroll run cannot be generated for a future month");
    if (runs.existsByPayrollMonth(month.toString())) conflict("A payroll run already exists for " + month);
    List<Employee> eligible = employees.findAll().stream().filter(employee -> employee.getStatus() == EmploymentStatus.ACTIVE).toList();
    List<PayrollStructure> eligibleStructures = eligible.stream().map(employee -> structures.findByEmployeeId(employee.getId()).orElse(null))
        .filter(structure -> structure != null && structure.isActive() && !structure.getEffectiveFrom().isAfter(month.atDay(1)))
        .toList();
    if (eligibleStructures.isEmpty()) conflict("No active employees have an effective salary structure for " + month);

    PayrollRun run;
    try {
      run = runs.saveAndFlush(new PayrollRun(month, resolveEmail(actor)));
    } catch (DataIntegrityViolationException duplicateMonth) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A payroll run already exists for " + month);
    }
    for (PayrollStructure structure : eligibleStructures) {
      List<PayrollDtos.ComponentSnapshot> details = structure.getComponents().stream().map(PayrollDtos.ComponentSnapshot::from).toList();
      PayrollCalculation.Result result = calculate(structure.getBaseSalary(), details, structure.getTaxRatePercent());
      payslips.save(new Payslip(run, structure.getEmployee(), structure.getCurrency(), result, serialize(details)));
    }
    entityManager.flush();
    return runResponse(run);
  }

  @Transactional(readOnly = true)
  public List<PayrollDtos.RunResponse> runs() {
    return runs.findAllByOrderByPayrollMonthDesc().stream().map(this::runResponse).toList();
  }

  @Transactional
  public PayrollDtos.RunResponse approve(UUID runId, String actor) {
    PayrollRun run = lockRun(runId);
    if (run.getStatus() != PayrollRunStatus.DRAFT) conflict("Only draft payroll runs can be approved");
    run.approve(resolveEmail(actor));
    publishPayrollStatus(run);
    return runResponse(run);
  }

  @Transactional
  public PayrollDtos.RunResponse markPaid(UUID runId, String actor) {
    PayrollRun run = lockRun(runId);
    if (run.getStatus() != PayrollRunStatus.APPROVED) conflict("Only approved payroll runs can be marked paid");
    run.pay(resolveEmail(actor));
    publishPayrollStatus(run);
    return runResponse(run);
  }

  private void publishPayrollStatus(PayrollRun run) {
    List<String> recipients = payslips.findByPayrollRunIdOrderByEmployeeLastNameAscEmployeeFirstNameAsc(run.getId())
        .stream().map(payslip -> payslip.getEmployee().getWorkEmail()).distinct().toList();
    events.publishEvent(new com.nexushr.notification.NotificationEvents.PayrollStatus(
        run.getMonth().toString(), run.getStatus().name(), run.getId().toString(), recipients));
  }

  @Transactional(readOnly = true)
  public PayrollDtos.PageResponse<PayrollDtos.PayslipResponse> runPayslips(UUID runId, int page, int size) {
    PayrollRun run = runs.findById(runId).orElseThrow(() -> notFound("Payroll run not found"));
    validatePage(page, size);
    var result = payslips.findByPayrollRunIdOrderByEmployeeLastNameAscEmployeeFirstNameAsc(runId,
        PageRequest.of(page, size, Sort.by("employee.lastName").ascending().and(Sort.by("employee.firstName").ascending())));
    return new PayrollDtos.PageResponse<>(result.getContent().stream().map(this::payslipResponse).toList(),
        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<PayrollDtos.PayslipResponse> ownPayslips(String principal) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(principal))
        .orElseThrow(() -> notFound("Employee profile not found"));
    return payslips.findByEmployeeIdOrderByPayrollRunPayrollMonthDesc(employee.getId()).stream().map(this::payslipResponse).toList();
  }

  @Transactional(readOnly = true)
  public PayrollDtos.PayslipResponse ownPayslip(String principal, UUID payslipId) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(principal))
        .orElseThrow(() -> notFound("Employee profile not found"));
    Payslip payslip = payslips.findByIdAndEmployeeId(payslipId, employee.getId())
        .orElseThrow(() -> notFound("Payslip not found"));
    return payslipResponse(payslip);
  }

  @Transactional
  public PayrollDtos.CorrectionResponse correct(UUID payslipId, PayrollDtos.CorrectionRequest request, String actor) {
    Payslip existing = payslips.findById(payslipId).orElseThrow(() -> notFound("Payslip not found"));
    PayrollRun lockedRun = lockRun(existing.getPayrollRun().getId());
    if (lockedRun.getStatus() != PayrollRunStatus.DRAFT) conflict("Payslips can be corrected only while the payroll run is draft");
    Payslip payslip = payslips.findByIdAndPayrollRunId(payslipId, lockedRun.getId())
        .orElseThrow(() -> notFound("Payslip not found"));
    List<PayrollDtos.ComponentSnapshot> details = new ArrayList<>(deserialize(payslip.getComponentDetails()));
    for (PayrollDtos.ComponentRequest adjustment : request.adjustments()) {
      details.add(new PayrollDtos.ComponentSnapshot("Correction: " + adjustment.name().trim(), adjustment.type(), adjustment.amount()));
    }
    BigDecimal taxRate = request.taxRatePercent() == null ? payslip.getTaxRatePercent() : request.taxRatePercent();
    PayrollCalculation.Result result = calculate(payslip.getBaseSalary(), details, taxRate);
    String updatedDetails = serialize(details);
    PayslipCorrection correction = new PayslipCorrection(payslip, result, updatedDetails, request.reason().trim(), resolveEmail(actor));
    payslip.correct(result, updatedDetails);
    corrections.save(correction);
    return PayrollDtos.CorrectionResponse.from(correction);
  }

  @Transactional(readOnly = true)
  public List<PayrollDtos.CorrectionResponse> correctionHistory(UUID payslipId) {
    if (!payslips.existsById(payslipId)) throw notFound("Payslip not found");
    return corrections.findByPayslipIdOrderByCorrectedAtDesc(payslipId).stream()
        .map(PayrollDtos.CorrectionResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public String exportCsv(UUID runId) {
    PayrollRun run = runs.findById(runId).orElseThrow(() -> notFound("Payroll run not found"));
    StringBuilder csv = new StringBuilder("payroll_month,employee_code,employee_name,currency,base_salary,allowances,deductions,gross_pay,tax_rate_percent,tax_amount,net_pay,status\r\n");
    for (Payslip payslip : payslips.findByPayrollRunIdOrderByEmployeeLastNameAscEmployeeFirstNameAsc(runId)) {
      String name = payslip.getEmployee().getFirstName() + " " + payslip.getEmployee().getLastName();
      csv.append(run.getMonth()).append(',').append(csv(payslip.getEmployee().getEmployeeCode())).append(',')
          .append(csv(name)).append(',').append(payslip.getCurrency()).append(',').append(payslip.getBaseSalary()).append(',')
          .append(payslip.getAllowanceTotal()).append(',').append(payslip.getDeductionTotal()).append(',')
          .append(payslip.getGrossPay()).append(',').append(payslip.getTaxRatePercent()).append(',')
          .append(payslip.getTaxAmount()).append(',').append(payslip.getNetPay()).append(',').append(run.getStatus()).append("\r\n");
    }
    return csv.toString();
  }

  private PayrollRun lockRun(UUID id) {
    PayrollRun run = runs.findById(id).orElseThrow(() -> notFound("Payroll run not found"));
    return entityManager.find(PayrollRun.class, run.getId(), LockModeType.PESSIMISTIC_WRITE);
  }

  private PayrollDtos.RunResponse runResponse(PayrollRun run) {
    return PayrollDtos.RunResponse.from(run, payslips.countByPayrollRunId(run.getId()));
  }

  private PayrollDtos.PayslipResponse payslipResponse(Payslip payslip) {
    return new PayrollDtos.PayslipResponse(payslip.getId(), payslip.getPayrollRun().getId(), payslip.getPayrollRun().getMonth(),
        payslip.getEmployee().getId(), payslip.getEmployee().getFirstName() + " " + payslip.getEmployee().getLastName(),
        payslip.getEmployee().getEmployeeCode(), payslip.getCurrency(), payslip.getBaseSalary(), payslip.getAllowanceTotal(),
        payslip.getDeductionTotal(), payslip.getGrossPay(), payslip.getTaxRatePercent(), payslip.getTaxAmount(),
        payslip.getNetPay(), payslip.getPayrollRun().getStatus(), deserialize(payslip.getComponentDetails()));
  }

  private PayrollCalculation.Result calculate(BigDecimal base, List<PayrollDtos.ComponentSnapshot> components, BigDecimal rate) {
    try { return PayrollCalculation.calculate(base, components, rate); }
    catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage()); }
  }

  private String serialize(List<PayrollDtos.ComponentSnapshot> components) {
    try { return json.writeValueAsString(components); }
    catch (JsonProcessingException impossible) { throw new IllegalStateException("Could not record payroll component details", impossible); }
  }

  private List<PayrollDtos.ComponentSnapshot> deserialize(String value) {
    try { return json.readValue(value, new TypeReference<>() {}); }
    catch (JsonProcessingException invalid) { throw new IllegalStateException("Stored payroll component details are invalid", invalid); }
  }

  private String resolveEmail(String principal) {
    try { return users.findById(UUID.fromString(principal)).map(UserAccount::getEmail).orElse(principal); }
    catch (IllegalArgumentException notUuid) { return principal; }
  }

  private static String csv(String value) {
    String safe = value;
    if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }
  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) badRequest("page must be non-negative and size must be between 1 and 100");
  }
  private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
  private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
  private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
