package org.opentripplanner.transit.model.timetable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;

class TimetableTest {

  @ParameterizedTest
  @CsvSource(
    value = """
    Description           | Timetable      | Expected number of days
    Same day              | 08:00 22:00    | 0
    Same day, exact limit | 08:00 23:59    | 0
    Night bus             | 22:00 1:00+1d  | 1
    Overnight exact limit | 22:59 23:59+1d | 1
    2 overnights          | 1:00 1:00+2d   | 2
    """,
    delimiter = '|',
    useHeadersInDisplayName = true
  )
  void maxTripSpanDays(String testCaseName, String schedule, int expectedNumberOfDays) {
    var timetable = Timetable.of()
      .addTripTimes(
        ScheduledTripTimes.of()
          .withTrip(TimetableRepositoryForTest.trip("t1").build())
          .withDepartureTimes(schedule)
          .build()
      )
      .build();

    assertEquals(expectedNumberOfDays, timetable.getMaxTripSpanDays());
  }
}
