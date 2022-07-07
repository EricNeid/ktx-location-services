/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
package org.neidhardt.ktxlocation.services

import android.content.Context
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test


@LargeTest
class BearingSensorServiceTest {

	private lateinit var context: Context
	private lateinit var unit: BearingSensorService

	@Before
	fun setUp() {
		context = getInstrumentation().targetContext
		unit = BearingSensorService(context)
	}

	@Test
	@kotlinx.coroutines.ExperimentalCoroutinesApi
	fun getBearingUpdatesFromMagneticAndAccelerometer() = runTest {
		// action
		val result = unit.getBearingUpdatesFromMagneticAndAccelerometer().first()
		// verify
		assertNotNull(result)
		assertEquals(result.azimuth, unit.lastKnownBearing)
	}

	@Test
	@kotlinx.coroutines.ExperimentalCoroutinesApi
	fun getBearingUpdatesFromRotation() = runTest {
		// action
		val result = unit.getBearingUpdatesFromRotation().first()
		// verify
		assertNotNull(result)
		assertEquals(result.azimuth, unit.lastKnownBearing)
	}
}