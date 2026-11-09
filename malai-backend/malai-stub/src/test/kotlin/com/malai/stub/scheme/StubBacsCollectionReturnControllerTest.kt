package com.malai.stub.scheme

import com.malai.scheme.contracts.bacs.BacsSchemeCollectionReturn
import com.malai.scheme.contracts.bacs.BacsSchemeParty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class StubBacsCollectionReturnControllerTest {

    private val controller = StubBacsCollectionReturnController()

    @Test
    fun acknowledgesACollectionReturnWith202() {
        val response = controller.returns(
            BacsSchemeCollectionReturn(
                endToEndId = UUID.randomUUID(),
                creditor = BacsSchemeParty("60-00-01", "22334455", "Octopus Energy Ltd"),
                reasonCode = "0",
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
    }
}
