package org.opentripplanner.transit.model;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.opentripplanner.OtpArchitectureModules.FRAMEWORK;
import static org.opentripplanner.OtpArchitectureModules.FRAMEWORK_UTILS;
import static org.opentripplanner.OtpArchitectureModules.GEO_UTILS;
import static org.opentripplanner.OtpArchitectureModules.GOOGLE_COLLECTIONS;
import static org.opentripplanner.OtpArchitectureModules.JACKSON_ANNOTATIONS;
import static org.opentripplanner.OtpArchitectureModules.OTP_ROOT;
import static org.opentripplanner.OtpArchitectureModules.RAPTOR_ADAPTER_API;
import static org.opentripplanner.OtpArchitectureModules.RAPTOR_API;
import static org.opentripplanner.OtpArchitectureModules.TRANSIT_MODEL;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner._support.arch.ArchComponent;
import org.opentripplanner._support.arch.Package;

public class TimetableRepositoryArchitectureTest {

  private static final Package TRANSIT_FRAMEWORK = TRANSIT_MODEL.subPackage("framework");
  private static final Package BASIC = TRANSIT_MODEL.subPackage("basic");
  private static final Package ORGANIZATION = TRANSIT_MODEL.subPackage("organization");
  private static final Package NETWORK = TRANSIT_MODEL.subPackage("network");
  private static final Package SITE = TRANSIT_MODEL.subPackage("site");
  private static final Package TIMETABLE = TRANSIT_MODEL.subPackage("timetable");
  private static final Package TIMETABLE_BOOKING = TIMETABLE.subPackage("booking");
  private static final Package LEGACY_MODEL = OTP_ROOT.subPackage("model");

  /**
   * Grandfathered network classes that currently depend on timetable. No new classes should be
   * added to this set. See doc/dev/transit-network-timetable-cycle-remediation-plan.md
   */
  private static final Set<String> GRANDFATHERED_NETWORK_TO_TIMETABLE = Set.of(
    "org.opentripplanner.transit.model.network.TripPattern",
    "org.opentripplanner.transit.model.network.TripPatternBuilder",
    "org.opentripplanner.transit.model.network.ReplacedByRelation",
    "org.opentripplanner.transit.model.network.ReplacementForRelation",
    "org.opentripplanner.transit.model.network.grouppriority.TransitGroupPriorityService",
    "org.opentripplanner.transit.model.network.grouppriority.TripAdapter"
  );

  @Test
  void enforceFrameworkPackageDependencies() {
    TRANSIT_FRAMEWORK.dependsOn(FRAMEWORK_UTILS).verify();
  }

  @Test
  void enforceBasicPackageDependencies() {
    var resources = FRAMEWORK.subPackage("resources");
    BASIC.dependsOn(FRAMEWORK_UTILS, GEO_UTILS, resources, TRANSIT_FRAMEWORK).verify();
  }

  @Test
  void enforceOrganizationPackageDependencies() {
    ORGANIZATION.dependsOn(FRAMEWORK_UTILS, TRANSIT_FRAMEWORK, BASIC).verify();
  }

  @Test
  void enforceSitePackageDependencies() {
    SITE.dependsOn(
      FRAMEWORK_UTILS,
      JACKSON_ANNOTATIONS,
      GEO_UTILS,
      TRANSIT_FRAMEWORK,
      BASIC,
      ORGANIZATION
    ).verify();
  }

  @Test
  void enforceNetworkPackageDependencies() {
    // Timetable dependency is tracked for removal, see
    // doc/dev/transit-network-timetable-cycle-remediation-plan.md
    NETWORK.dependsOn(
      FRAMEWORK_UTILS,
      GEO_UTILS,
      TRANSIT_FRAMEWORK,
      BASIC,
      ORGANIZATION,
      SITE,
      TIMETABLE,
      LEGACY_MODEL,
      RAPTOR_API,
      RAPTOR_ADAPTER_API
    ).verify();
  }

  @Test
  void enforceTimetablePackageDependencies() {
    TIMETABLE.dependsOn(
      GOOGLE_COLLECTIONS,
      FRAMEWORK_UTILS,
      TRANSIT_FRAMEWORK,
      BASIC,
      ORGANIZATION,
      NETWORK,
      SITE,
      TIMETABLE_BOOKING,
      LEGACY_MODEL
    ).verify();
  }

  @Test
  // Disabled until network<->timetable cycle is fully resolved, see
  // doc/dev/transit-network-timetable-cycle-remediation-plan.md
  @Disabled
  void enforceNoCyclicDependencies() {
    slices()
      .matching(TRANSIT_MODEL.packageIdentifierAllSubPackages())
      .should()
      .beFreeOfCycles()
      .check(ArchComponent.OTP_CLASSES);
  }

  /**
   * Freeze test: prevent new network -> timetable dependencies from being introduced. Only the
   * grandfathered classes listed in {@link #GRANDFATHERED_NETWORK_TO_TIMETABLE} are allowed to
   * depend on timetable. See doc/dev/transit-network-timetable-cycle-remediation-plan.md
   */
  @Test
  void noNewNetworkToTimetableDependencies() {
    var notGrandfathered = DescribedPredicate.describe(
      "not in the grandfathered network->timetable set",
      (JavaClass javaClass) ->
        !GRANDFATHERED_NETWORK_TO_TIMETABLE.contains(javaClass.getName())
    );

    noClasses()
      .that()
      .resideInAPackage("org.opentripplanner.transit.model.network..")
      .and(notGrandfathered)
      .should()
      .dependOnClassesThat()
      .resideInAPackage("org.opentripplanner.transit.model.timetable..")
      .check(ArchComponent.OTP_CLASSES);
  }
}
