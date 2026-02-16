package org.opentripplanner.graph_builder.module;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Adjust all scheduled times to match the transit model timezone.
 */
public class TimeZoneAdjusterModule implements GraphBuilderModule {

  private final TimetableRepository timetableRepository;

  @Inject
  public TimeZoneAdjusterModule(TimetableRepository timetableRepository) {
    this.timetableRepository = timetableRepository;
  }

  @Override
  public void buildGraph() {
    // TODO: We assume that all time zones follow the same DST rules. In reality we need to split up
    //  the services for each DST transition
    final Instant serviceStart = timetableRepository.getTransitServiceStarts();
    var graphOffset = Duration.ofSeconds(
      timetableRepository.getTimeZone().getRules().getOffset(serviceStart).getTotalSeconds()
    );

    Map<ZoneId, Duration> agencyShift = new HashMap<>();

    timetableRepository
      .getAllTripPatterns()
      .forEach(pattern -> {
        var timeShift = agencyShift.computeIfAbsent(
          pattern.getRoute().getAgency().getTimezone(),
          zoneId ->
            (graphOffset.minusSeconds(zoneId.getRules().getOffset(serviceStart).getTotalSeconds()))
        );

        if (timeShift.isZero()) {
          return;
        }

        var adjustedTimetable = timetableRepository
          .getScheduledTimetable(pattern)
          .copyOf()
          .withAdjustedTimes(timeShift)
          .build();
        adjustedTimetable.setPattern(pattern);
        // Replace the timetable for this pattern in the repository
        timetableRepository.addScheduledTimetable(pattern.getId(), adjustedTimetable);
      });
    timetableRepository.index();
  }
}
