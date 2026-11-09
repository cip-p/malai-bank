package com.malai.accounts.identifier

import com.malai.accounts.support.SpringTest
import com.malai.commons.banking.MalaiSortCode
import com.malai.commons.banking.VocaLinkModulus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@SpringTest
class AccountNumberGeneratorTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val malaiBank = BankIdentifierProperties(sortCode = MalaiSortCode.CANONICAL, bic = "MALAGB22XXX")

    private lateinit var generator: AccountNumberGenerator

    @BeforeEach
    fun setUp() {
        generator = AccountNumberGeneratorConfiguration().accountNumberGenerator(jdbcTemplate, malaiBank)
        jdbcTemplate.execute("ALTER SEQUENCE account_number_seq RESTART WITH 10000000")
    }

    @Test
    fun firstCallReturnsTheStartingValueOfTheSequence() {
        // VALACDOS has zero rules covering 65-XX-XX (Plan 02-09 verification), so every
        // candidate from the sequence passes the modulus check on first try under MALAI prefix.
        assertThat(generator.generate()).isEqualTo("10000000")
    }

    @Test
    fun successiveCallsAreMonotonicAndExactlyEightDigits() {
        val numbers = (1..5).map { generator.generate() }

        assertThat(numbers).containsExactly(
            "10000000",
            "10000001",
            "10000002",
            "10000003",
            "10000004",
        )
        assertThat(numbers).allMatch { it.length == 8 }
        assertThat(numbers).allMatch { VocaLinkModulus.isValid(MalaiSortCode.CANONICAL, it) }
    }

    @Test
    fun beanConstructionFailsFastForNonMalaiSortCode() {
        // Misconfiguration must be a boot-time failure, not a runtime surprise after
        // accounts have been issued under the wrong prefix.
        val foreignBank = BankIdentifierProperties(sortCode = "01-23-45", bic = "OTHERGB22XXX")

        assertThatThrownBy {
            AccountNumberGeneratorConfiguration().accountNumberGenerator(jdbcTemplate, foreignBank)
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("01-23-45")
            .hasMessageContaining(MalaiSortCode.PREFIX)
    }
}
