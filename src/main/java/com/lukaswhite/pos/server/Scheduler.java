package com.lukaswhite.pos.server;

/* ------ Scheduler.java -----
 * --- --- --- --- --- --- ---
 * - Handles FCFS scheduling per service request.
 * - Considers Bay working hours
 * - Avoid overlaps with lunch and end of day
 * - Each bay works Mon-Fri 08:00-12:00 and 13:00-17:00.
 * - Fills schedule gaps appropriately
 * - Prefers Sue
 *
 * Ported into the distributed project with one behavioral addition:
 * scheduleSingleJob() now returns an Appointment (bay + start time)
 * instead of void, so the network/GUI scheduling flow can tell the
 * person exactly when and with whom they're booked. DataImporter's bulk
 * import just ignores the return value, same as it always did.
*/

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scheduler {

    private static final LocalTime WORK_START = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END   = LocalTime.of(13, 0);
    private static final LocalTime WORK_END   = LocalTime.of(17, 0);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * What scheduleSingleJob() hands back: which bay the job landed in,
     * and when it starts. A "record" is a newer bit of Java (16+) - it's
     * just a compact way to write a small, immutable data-holder class
     * without hand-writing a constructor/getters/equals/toString.
     */
    public record Appointment(int bay, LocalDateTime start) {}

    // ------ subtract -------
    // Given an interval (free) that holds the time of the day we haven't yet determined free or not
    // - this function removes the interval (s, e) from it.
    // [8:00, 12:00], (8:00), (10:00) -> [10:00, 12:00]
    private static void subtract(List<Interval> free, LocalTime s, LocalTime e) {
        if (!s.isBefore(e)) return;

        List<Interval> result = new ArrayList<>();
        for (Interval iv : free) {
            if (e.compareTo(iv.start) <= 0 || s.compareTo(iv.end) >= 0) {
                result.add(iv); // no overlap
            } else {
                if (s.isAfter(iv.start)) result.add(new Interval(iv.start, s));
                if (e.isBefore(iv.end)) result.add(new Interval(e, iv.end));
            }
        }
        free.clear();
        free.addAll(result);
    }

    // ------ nextMonday ------
    private static LocalDateTime nextMonday() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        if (!today.isBefore(monday)) monday = monday.plusWeeks(1);
        return LocalDateTime.of(monday, WORK_START);
    }

    // ------ nextWeekday ------
    private static LocalDate nextWeekday(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)
            d = d.plusDays(1);
        return d;
    }

    // ------ Interval.class ------
    private static class Interval {
        final LocalTime start, end;
        Interval(LocalTime s, LocalTime e) { this.start = s; this.end = e; }
    }

    // ------ findEarliestStart ------
    private static LocalDateTime findEarliestStart(Connection con, int bay, int duration) throws SQLException {
        LocalDate day = nextMonday().toLocalDate();

        while (true) {
            day = nextWeekday(day);

            List<Interval> free = new ArrayList<>();
            free.add(new Interval(WORK_START, LUNCH_START));
            free.add(new Interval(LUNCH_END, WORK_END));

            try (PreparedStatement pst = con.prepareStatement("""
                SELECT APPOINTMENT_TIME, SERVICE_TUID FROM SCHEDULE_TABLE
                WHERE BAY_TUID = ? AND DATE(APPOINTMENT_TIME) = ?
                ORDER BY APPOINTMENT_TIME
            """)) {
                pst.setInt(1, bay);
                pst.setString(2, day.toString());

                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    LocalDateTime start = LocalDateTime.parse(rs.getString("APPOINTMENT_TIME"), DATE_FMT);
                    int sid = rs.getInt("SERVICE_TUID");
                    int dur = Math.max(30, DBManager.getServiceTime(sid));

                    LocalTime s = start.toLocalTime();
                    LocalTime e = s.plusMinutes(dur);
                    subtract(free, s, e);
                }
            }

            free.sort(Comparator.comparing(iv -> iv.start));

            for (Interval iv : free) {
                long available = Duration.between(iv.start, iv.end).toMinutes();
                if (available >= duration) {
                    return LocalDateTime.of(day, iv.start);
                }
            }

            day = nextWeekday(day.plusDays(1));
        }
    }

    // ------ scheduleSingleJob ------
    // Given a vehicle + service, find the earliest slot across both bays
    // and insert the appointment. Returns the Appointment that was booked
    // (bay + start time) so callers - the GUI's "SCHEDULE" request in
    // particular - can build a friendly confirmation message.
    public static Appointment scheduleSingleJob(int vehicleTUID, int serviceTUID) throws SQLException {
        try (Connection con = DBManager.getConnection()) {

            int duration = Math.max(30, DBManager.getServiceTime(serviceTUID));

            LocalDateTime bay1Start = findEarliestStart(con, 1, duration);
            LocalDateTime bay2Start = findEarliestStart(con, 2, duration);

            int chosenBay;
            LocalDateTime chosenStart;
            if (bay2Start.isBefore(bay1Start) || bay2Start.isEqual(bay1Start)) {
                chosenBay = 2;
                chosenStart = bay2Start;
            } else {
                chosenBay = 1;
                chosenStart = bay1Start;
            }

            try (PreparedStatement pst = con.prepareStatement("""
                INSERT INTO SCHEDULE_TABLE (VEHICLE_TUID, SERVICE_TUID, BAY_TUID, APPOINTMENT_TIME)
                VALUES (?, ?, ?, ?)
            """)) {
                pst.setInt(1, vehicleTUID);
                pst.setInt(2, serviceTUID);
                pst.setInt(3, chosenBay);
                pst.setString(4, chosenStart.format(DATE_FMT));
                pst.executeUpdate();
            }

            return new Appointment(chosenBay, chosenStart);
        }
    }
}
