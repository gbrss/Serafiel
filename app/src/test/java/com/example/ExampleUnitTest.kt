package com.example

import com.example.data.LocationHelper
import com.example.model.ProximityBeacon
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun defaultBeaconRadius_isWithinRange() {
    val beacon = ProximityBeacon(userId = "u1", displayName = "Ana")
    assertEquals(50L, beacon.radiusKm)
  }

  @Test
  fun distanceKm_filtersWithin1000Km() {
    // Madrid -> Barcelona (~505 km) should be included in 1000 km radius, excluded in 100 km
    val madridToBcn = LocationHelper.distanceKm(40.42, -3.70, 41.39, 2.17)
    assertTrue("Madrid to Barcelona should be ~505 km, was $madridToBcn", madridToBcn in 490.0..520.0)
    assertTrue(madridToBcn <= 1000.0)
    assertFalse(madridToBcn <= 100.0)

    // Madrid -> Berlin (~1870 km) should be excluded even at 1000 km
    val madridToBerlin = LocationHelper.distanceKm(40.42, -3.70, 52.52, 13.40)
    assertTrue("Madrid to Berlin should be > 1000 km, was $madridToBerlin", madridToBerlin > 1000.0)
  }
}
