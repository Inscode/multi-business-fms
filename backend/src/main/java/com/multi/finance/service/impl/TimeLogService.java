package com.multi.finance.service.impl;

import com.multi.finance.dto.request.AttendanceRequest;
import com.multi.finance.dto.request.HolidayRequest;
import com.multi.finance.dto.request.TimeEntryRequest;
import com.multi.finance.dto.response.CompanyHolidayResponse;
import com.multi.finance.dto.response.WorkerAttendanceResponse;
import com.multi.finance.dto.response.WorkerAverageHoursResponse;
import com.multi.finance.dto.response.WorkerResponse;
import com.multi.finance.dto.response.WorkerTimeEntryResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.AttendanceType;
import com.multi.finance.enums.HolidayType;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeLogService {

    private final WorkerAttendanceRepository attendanceRepo;
    private final WorkerTimeEntryRepository  timeEntryRepo;
    private final CompanyHolidayRepository   holidayRepo;
    private final WorkerRepository           workerRepo;

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ── Attendance status ─────────────────────────────────────────────

    @Transactional
    public WorkerAttendanceResponse recordAttendance(AttendanceRequest req) {
        Worker worker = workerRepo.findById(req.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        AttendanceType type = AttendanceType.valueOf(req.getAttendanceType());

        WorkerAttendance record = attendanceRepo
                .findByWorkerIdAndAttendanceDate(req.getWorkerId(), req.getDate())
                .orElse(WorkerAttendance.builder()
                        .worker(worker)
                        .attendanceDate(req.getDate())
                        .createdAt(LocalDateTime.now())
                        .build());

        record.setAttendanceType(type);
        record.setNotes(req.getNotes());
        record.setRecordedBy(currentUser());
        return toAttendanceResponse(attendanceRepo.save(record));
    }

    @Transactional(readOnly = true)
    public List<WorkerAttendanceResponse> getAttendanceForDate(LocalDate date) {
        return attendanceRepo.findByAttendanceDateOrderByWorkerFullNameAsc(date)
                .stream().map(this::toAttendanceResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkerAttendanceResponse> getWorkerAttendance(Long workerId, LocalDate from, LocalDate to) {
        return attendanceRepo.findByWorkerIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(workerId, from, to)
                .stream().map(this::toAttendanceResponse).collect(Collectors.toList());
    }

    // ── Time entries (clock in / out) ─────────────────────────────────

    @Transactional
    public WorkerTimeEntryResponse addTimeEntry(TimeEntryRequest req) {
        Worker worker = workerRepo.findById(req.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        if (req.getClockOut() != null && req.getClockOut().isBefore(req.getClockIn())) {
            throw new RuntimeException("Clock-out time cannot be before clock-in time");
        }

        WorkerTimeEntry entry = WorkerTimeEntry.builder()
                .worker(worker)
                .entryDate(req.getDate())
                .clockIn(req.getClockIn())
                .clockOut(req.getClockOut())
                .notes(req.getNotes())
                .recordedBy(currentUser())
                .createdAt(LocalDateTime.now())
                .build();

        // Auto-mark as PRESENT if no attendance record yet
        attendanceRepo.findByWorkerIdAndAttendanceDate(req.getWorkerId(), req.getDate())
                .orElseGet(() -> attendanceRepo.save(WorkerAttendance.builder()
                        .worker(worker)
                        .attendanceDate(req.getDate())
                        .attendanceType(AttendanceType.PRESENT)
                        .recordedBy(currentUser())
                        .createdAt(LocalDateTime.now())
                        .build()));

        return toEntryResponse(timeEntryRepo.save(entry));
    }

    @Transactional
    public WorkerTimeEntryResponse updateTimeEntry(Long entryId, TimeEntryRequest req) {
        WorkerTimeEntry entry = timeEntryRepo.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Time entry not found"));

        if (req.getClockOut() != null && req.getClockOut().isBefore(req.getClockIn())) {
            throw new RuntimeException("Clock-out time cannot be before clock-in time");
        }

        entry.setClockIn(req.getClockIn());
        entry.setClockOut(req.getClockOut());
        entry.setNotes(req.getNotes());
        return toEntryResponse(timeEntryRepo.save(entry));
    }

    @Transactional
    public void deleteTimeEntry(Long entryId) {
        timeEntryRepo.deleteById(entryId);
    }

    @Transactional(readOnly = true)
    public List<WorkerTimeEntryResponse> getTimeEntriesForDate(LocalDate date) {
        return timeEntryRepo.findByEntryDateOrderByWorkerFullNameAscClockInAsc(date)
                .stream().map(this::toEntryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkerTimeEntryResponse> getWorkerTimeEntries(Long workerId, LocalDate date) {
        return timeEntryRepo.findByWorkerIdAndEntryDateOrderByClockInAsc(workerId, date)
                .stream().map(this::toEntryResponse).collect(Collectors.toList());
    }

    // ── Holidays ──────────────────────────────────────────────────────

    @Transactional
    public CompanyHolidayResponse createHoliday(HolidayRequest req) {
        CompanyHoliday holiday = CompanyHoliday.builder()
                .holidayDate(req.getDate())
                .name(req.getName())
                .holidayType(HolidayType.valueOf(req.getHolidayType()))
                .appliesToAll(req.isAppliesToAll())
                .createdBy(currentUser())
                .createdAt(LocalDateTime.now())
                .build();

        if (!req.isAppliesToAll() && req.getWorkerIds() != null && !req.getWorkerIds().isEmpty()) {
            holiday.setSpecificWorkers(new HashSet<>(workerRepo.findAllById(req.getWorkerIds())));
        }
        return toHolidayResponse(holidayRepo.save(holiday));
    }

    @Transactional(readOnly = true)
    public List<CompanyHolidayResponse> getHolidays(LocalDate from, LocalDate to) {
        return holidayRepo.findWithWorkersByDateRange(from, to)
                .stream().map(this::toHolidayResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepo.deleteById(id);
    }

    // ── Toggle time-log participation ─────────────────────────────────

    @Transactional
    public WorkerResponse toggleTimeLogActive(Long workerId) {
        Worker worker = workerRepo.findById(workerId)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        worker.setTimeLogActive(!Boolean.TRUE.equals(worker.getTimeLogActive()));
        Worker saved = workerRepo.save(worker);
        return WorkerResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .workerType(saved.getWorkerType())
                .baseSalary(saved.getBaseSalary())
                .active(saved.getActive())
                .joinedDate(saved.getJoinedDate())
                .notes(saved.getNotes())
                .createdAt(saved.getCreatedAt())
                .timeLogActive(saved.getTimeLogActive())
                .build();
    }

    // ── Average hours ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkerAverageHoursResponse> getAverageHours(LocalDate from, LocalDate to) {
        // Only workers marked as time-log participants
        List<Worker> workers = workerRepo.findByActiveTrueAndTimeLogActiveTrueOrderByFullNameAsc();

        List<CompanyHoliday> allHolidays = holidayRepo.findWithWorkersByDateRange(from, to);

        // Group attendance by workerId
        Map<Long, List<WorkerAttendance>> attByWorker =
                attendanceRepo.findByDateRangeWithWorkers(from, to)
                        .stream().collect(Collectors.groupingBy(a -> a.getWorker().getId()));

        // Group time entries by workerId then by date
        Map<Long, Map<LocalDate, List<WorkerTimeEntry>>> entriesByWorkerDate =
                timeEntryRepo.findByDateRangeWithWorkers(from, to)
                        .stream().collect(Collectors.groupingBy(
                                e -> e.getWorker().getId(),
                                Collectors.groupingBy(WorkerTimeEntry::getEntryDate)));

        return workers.stream().map(worker -> {
            int expectedDays = 0;
            int holidayDays  = 0;

            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                if (cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    if (isHolidayForWorker(cursor, worker.getId(), allHolidays)) holidayDays++;
                    else expectedDays++;
                }
                cursor = cursor.plusDays(1);
            }

            List<WorkerAttendance> records = attByWorker.getOrDefault(worker.getId(), List.of());
            int presentDays = 0, halfDays = 0, absentDays = 0, indivHolidays = 0;

            for (WorkerAttendance r : records) {
                switch (r.getAttendanceType()) {
                    case PRESENT  -> presentDays++;
                    case HALF_DAY -> halfDays++;
                    case ABSENT   -> absentDays++;
                    case HOLIDAY  -> indivHolidays++;
                }
            }

            // Total worked minutes from all time entries with a clock_out
            Map<LocalDate, List<WorkerTimeEntry>> byDate =
                    entriesByWorkerDate.getOrDefault(worker.getId(), Map.of());

            long totalMinutes = byDate.values().stream()
                    .flatMap(List::stream)
                    .mapToLong(WorkerTimeEntry::workedMinutes)
                    .sum();

            BigDecimal totalHours = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            BigDecimal avgHours = expectedDays > 0
                    ? totalHours.divide(BigDecimal.valueOf(expectedDays), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return WorkerAverageHoursResponse.builder()
                    .workerId(worker.getId())
                    .workerName(worker.getFullName())
                    .workerType(worker.getWorkerType().name())
                    .expectedWorkingDays(expectedDays)
                    .presentDays(presentDays)
                    .halfDays(halfDays)
                    .absentDays(absentDays)
                    .holidayDays(holidayDays + indivHolidays)
                    .totalHoursLogged(totalHours)
                    .averageHoursPerDay(avgHours)
                    .build();
        }).collect(Collectors.toList());
    }

    private boolean isHolidayForWorker(LocalDate date, Long workerId, List<CompanyHoliday> holidays) {
        return holidays.stream().anyMatch(h ->
                h.getHolidayDate().equals(date) &&
                (h.getAppliesToAll() || h.getSpecificWorkers().stream()
                        .anyMatch(w -> w.getId().equals(workerId))));
    }

    // ── Mappers ───────────────────────────────────────────────────────

    private WorkerAttendanceResponse toAttendanceResponse(WorkerAttendance a) {
        return WorkerAttendanceResponse.builder()
                .id(a.getId())
                .workerId(a.getWorker().getId())
                .workerName(a.getWorker().getFullName())
                .date(a.getAttendanceDate().toString())
                .attendanceType(a.getAttendanceType().name())
                .hoursWorked(null)
                .notes(a.getNotes())
                .build();
    }

    private WorkerTimeEntryResponse toEntryResponse(WorkerTimeEntry e) {
        return WorkerTimeEntryResponse.builder()
                .id(e.getId())
                .workerId(e.getWorker().getId())
                .workerName(e.getWorker().getFullName())
                .date(e.getEntryDate().toString())
                .clockIn(e.getClockIn().toString())
                .clockOut(e.getClockOut() != null ? e.getClockOut().toString() : null)
                .workedMinutes(e.workedMinutes())
                .notes(e.getNotes())
                .build();
    }

    private CompanyHolidayResponse toHolidayResponse(CompanyHoliday h) {
        return CompanyHolidayResponse.builder()
                .id(h.getId())
                .date(h.getHolidayDate().toString())
                .name(h.getName())
                .holidayType(h.getHolidayType().name())
                .appliesToAll(h.getAppliesToAll())
                .workerNames(h.getSpecificWorkers().stream()
                        .map(Worker::getFullName).sorted().collect(Collectors.toList()))
                .createdAt(h.getCreatedAt().toString())
                .build();
    }
}
