package com.malai.accountholder.acceptance.sad

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.client.malai.accountholder.builders.request.CreateAccountHolderRequestBuilder.Companion.aCreateAccountHolderRequest
import com.malai.client.malai.accountholder.builders.request.CreateIndividualRequestBuilder.Companion.aCreateIndividualRequest
import com.malai.client.malai.accountholder.builders.request.UpdateAddressRequestBuilder.Companion.anUpdateAddressRequest
import com.malai.client.malai.accountholder.builders.request.UpdateEmailRequestBuilder.Companion.anUpdateEmailRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED

class AccountHolderControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun getAccountHolderReturns404WhenNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getAccountHolderNameReturns404WhenNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/name")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getIndividualReturns404WhenNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/individual")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun createIndividualReturns404WhenAccountHolderNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/account-holder/individual")
                .body(aCreateIndividualRequest()
                    .withTitle("Mr")
                    .withFirstName("John")
                    .withLastName("Doe")
                    .withDateOfBirth("1990-01-15")
                    .withEmail("john.doe@example.com")
                    .withPhone("+447123456789"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun updateEmailReturns404WhenIndividualNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(accountHolderServiceReceives(
            put()
                .url("/api/v1/account-holder/individual/email")
                .body(anUpdateEmailRequest()
                    .withEmail("new@example.com"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getJointAccountReturns404WhenNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/joint")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusinessReturns404WhenNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.BUSINESS))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/business")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusinessCorrespondenceAddressReturns404WhenNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.BUSINESS))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/business/correspondence-address")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusinessRegisteredAddressReturns404WhenNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.BUSINESS))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/business/registered-address")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSoleTraderReturns404WhenNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.SOLE_TRADER))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/sole-trader")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getAddressesReturns404WhenAccountHolderNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/addresses")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun updateAddressReturns404WhenAccountHolderNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/addresses")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
                .body(anUpdateAddressRequest()
                    .withLine1("789 New Street")
                    .withPostTown("Bristol")
                    .withPostCode("BS1 1AA")
                    .withCountryCode("GB")
                    .withFrom("2025-03-01"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getProfileImageReturns404WhenNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/profile-image")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun updateProfileImageReturns404WhenAccountHolderNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            put()
                .url("/api/v1/account-holder/profile-image")
                .contentType("image/jpeg")
                .body(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteProfileImageReturns404WhenAccountHolderNotFound() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            delete()
                .url("/api/v1/account-holder/profile-image")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }

    @Test
    fun getAllAccountHoldersReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/all")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun createAccountHolderReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/account-holder")
                .body(aCreateAccountHolderRequest()
                    .withAccountHolderType("INDIVIDUAL"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun createIndividualReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/account-holder/individual")
                .body(aCreateIndividualRequest()
                    .withTitle("Mr")
                    .withFirstName("John")
                    .withLastName("Doe")
                    .withDateOfBirth("1990-01-15")
                    .withEmail("john.doe@example.com")
                    .withPhone("+447123456789"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
