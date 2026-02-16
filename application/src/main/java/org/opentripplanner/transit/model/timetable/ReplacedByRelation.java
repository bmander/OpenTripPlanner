package org.opentripplanner.transit.model.timetable;

public class ReplacedByRelation {

  private final TripOnServiceDate tripOnServiceDate;

  public ReplacedByRelation(TripOnServiceDate tripOnServiceDate) {
    this.tripOnServiceDate = tripOnServiceDate;
  }

  public TripOnServiceDate getTripOnServiceDate() {
    return tripOnServiceDate;
  }
}
