package org.opentripplanner.model.plan.grouppriority;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.grouppriority.EntityAdapter;
import org.opentripplanner.transit.model.timetable.Trip;

public class TripAdapter implements EntityAdapter {

  private final Trip trip;

  public TripAdapter(Trip trip) {
    this.trip = trip;
  }

  @Override
  public TransitMode mode() {
    return trip.getMode();
  }

  @Override
  public String subMode() {
    return trip.getNetexSubMode().name();
  }

  @Override
  public FeedScopedId agencyId() {
    return trip.getRoute().getAgency().getId();
  }

  @Override
  public FeedScopedId routeId() {
    return trip.getRoute().getId();
  }
}
