package com.malai.test.acceptance.kensa

import com.malai.test.acceptance.kensa.fixtures.Ids
import com.malai.test.acceptance.kensa.fixtures.TestFixtures.someRandomPopName
import com.malai.test.acceptance.kensa.fixtures.TestFixtures.someRandomPremisesId
import com.malai.test.acceptance.kensa.fixtures.TestFixtures.someRandomServiceId
import dev.kensa.fixture.FixtureContainer
import dev.kensa.fixture.fixture
import dev.kensa.outputs.CapturedOutputContainer
import dev.kensa.outputs.capturedOutput

object KensaCapturedOutputs : CapturedOutputContainer {
    val Ids = capturedOutput<Ids>("IDs")
    val Ids2 = capturedOutput<Ids>("IDs2")
    val Ids3 = capturedOutput<Ids>("IDs3")
}

object KensaFixtures : FixtureContainer {
    val DataServiceIdFx = fixture("DataServiceId") { someRandomServiceId() }
    val PopNameFx = fixture("PopName") { someRandomPopName() }
    val PremisesIdFx = fixture("PremisesIdFx") { someRandomPremisesId() }
}
