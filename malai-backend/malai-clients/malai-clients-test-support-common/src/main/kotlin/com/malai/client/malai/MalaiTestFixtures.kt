package com.malai.client.malai

import dev.kensa.RenderedValue
import dev.kensa.context.RenderedValueInvocationContext
import dev.kensa.context.RenderedValueInvocationContextHolder
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.*

@ExtendWith(MalaiTestFixtures.MalaiTestFixturesExtension::class)
interface MalaiTestFixtures {

    @RenderedValue
    fun someAccountUid() = malaiTestFixturesContext.someAccountUid

    @RenderedValue
    fun anotherAccountUid() = malaiTestFixturesContext.anotherAccountUid

    @RenderedValue
    fun someCurrency() = malaiTestFixturesContext.someCurrency

    @RenderedValue
    fun someAccessToken() = malaiTestFixturesContext.someAccessToken

    @RenderedValue
    fun someSavingsGoalUid() = malaiTestFixturesContext.someSavingsGoalUid

    @RenderedValue
    fun anotherSavingsGoalUid() = malaiTestFixturesContext.anotherSavingsGoalUid

    @RenderedValue
    fun someTransferUid() = malaiTestFixturesContext.someTransferUid

    @RenderedValue
    fun someUserId() = malaiTestFixturesContext.someUserId

    @RenderedValue
    fun anotherUserId() = malaiTestFixturesContext.anotherUserId

    @RenderedValue
    fun someDefaultCategory() = malaiTestFixturesContext.someDefaultCategory

    @RenderedValue
    fun anotherDefaultCategory() = malaiTestFixturesContext.anotherDefaultCategory

    @RenderedValue
    fun someCategoryUid() = malaiTestFixturesContext.someCategoryUid

    @RenderedValue
    fun anotherCategoryUid() = malaiTestFixturesContext.anotherCategoryUid

    @RenderedValue
    fun someFeedItemUid() = malaiTestFixturesContext.someFeedItemUid

    @RenderedValue
    fun anotherFeedItemUid() = malaiTestFixturesContext.anotherFeedItemUid

    @RenderedValue
    fun somePayeeUid() = malaiTestFixturesContext.somePayeeUid

    @RenderedValue
    fun anotherPayeeUid() = malaiTestFixturesContext.anotherPayeeUid

    @RenderedValue
    fun somePayeeAccountUid() = malaiTestFixturesContext.somePayeeAccountUid

    @RenderedValue
    fun somePayeeName() = malaiTestFixturesContext.somePayeeName

    @RenderedValue
    fun someMandateUid() = malaiTestFixturesContext.someMandateUid

    @RenderedValue
    fun anotherMandateUid() = malaiTestFixturesContext.anotherMandateUid

    @RenderedValue
    fun someOriginatorUid() = malaiTestFixturesContext.someOriginatorUid

    @RenderedValue
    fun someMerchantUid() = malaiTestFixturesContext.someMerchantUid

    @RenderedValue
    fun someMandateReference() = malaiTestFixturesContext.someMandateReference

    @RenderedValue
    fun someDirectDebitPaymentUid() = malaiTestFixturesContext.someDirectDebitPaymentUid

    @RenderedValue
    fun someCardUid() = malaiTestFixturesContext.someCardUid

    @RenderedValue
    fun someCardAssociationUid() = malaiTestFixturesContext.someCardAssociationUid

    @RenderedValue
    fun somePaymentOrderUid() = malaiTestFixturesContext.somePaymentOrderUid

    @RenderedValue
    fun somePaymentUid() = malaiTestFixturesContext.somePaymentUid

    @RenderedValue
    fun anotherPaymentUid() = malaiTestFixturesContext.anotherPaymentUid

    @RenderedValue
    fun anotherPayeeAccountUid() = malaiTestFixturesContext.anotherPayeeAccountUid

    @RenderedValue
    fun someAccountHolderUid() = malaiTestFixturesContext.someAccountHolderUid

    @RenderedValue
    fun anotherAccountHolderUid() = malaiTestFixturesContext.anotherAccountHolderUid

    @RenderedValue
    fun someIndividualUid() = malaiTestFixturesContext.someIndividualUid

    @RenderedValue
    fun anotherIndividualUid() = malaiTestFixturesContext.anotherIndividualUid

    @RenderedValue
    fun someBusinessUid() = malaiTestFixturesContext.someBusinessUid

    @RenderedValue
    fun someBusinessAddressUid() = malaiTestFixturesContext.someBusinessAddressUid

    @RenderedValue
    fun anotherBusinessAddressUid() = malaiTestFixturesContext.anotherBusinessAddressUid

    @RenderedValue
    fun someSoleTraderUid() = malaiTestFixturesContext.someSoleTraderUid

    @RenderedValue
    fun someProfileImageUid() = malaiTestFixturesContext.someProfileImageUid

    @RenderedValue
    fun someAddressUid() = malaiTestFixturesContext.someAddressUid

    @RenderedValue
    fun anotherAddressUid() = malaiTestFixturesContext.anotherAddressUid

    @RenderedValue
    fun someTokenUid() = malaiTestFixturesContext.someTokenUid

    @RenderedValue
    fun anotherTokenUid() = malaiTestFixturesContext.anotherTokenUid

    @RenderedValue
    fun someApplicationUserUid() = malaiTestFixturesContext.someApplicationUserUid

    @RenderedValue
    fun someSpaceUid() = malaiTestFixturesContext.someSpaceUid

    @RenderedValue
    fun anotherSpaceUid() = malaiTestFixturesContext.anotherSpaceUid

    @RenderedValue
    fun someSettleUpProfileUid() = malaiTestFixturesContext.someSettleUpProfileUid

    @RenderedValue
    fun someFeedItemAttachmentUid() = malaiTestFixturesContext.someFeedItemAttachmentUid

    @RenderedValue
    fun someReceiptUid() = malaiTestFixturesContext.someReceiptUid

    @RenderedValue
    fun anotherReceiptUid() = malaiTestFixturesContext.anotherReceiptUid

    @RenderedValue
    fun someMastercardFeedItemUid() = malaiTestFixturesContext.someMastercardFeedItemUid

    @RenderedValue
    fun somePayeeImageUid() = malaiTestFixturesContext.somePayeeImageUid

    @RenderedValue
    fun someScheduledPaymentUid() = malaiTestFixturesContext.someScheduledPaymentUid

    @RenderedValue
    fun someRecurringTransferUid() = malaiTestFixturesContext.someRecurringTransferUid

    @RenderedValue
    fun someRoundUpGoalUid() = malaiTestFixturesContext.someRoundUpGoalUid

    @RenderedValue
    fun someCurrencyFlagUid() = malaiTestFixturesContext.someCurrencyFlagUid

    @RenderedValue
    fun someSortCodeIdentifierId() = malaiTestFixturesContext.someSortCodeIdentifierId

    @RenderedValue
    fun someIbanBicIdentifierId() = malaiTestFixturesContext.someIbanBicIdentifierId

    @RenderedValue
    fun someBankIdentifier() = malaiTestFixturesContext.someBankIdentifier

    @RenderedValue
    fun anotherBankIdentifier() = malaiTestFixturesContext.anotherBankIdentifier

    @RenderedValue
    fun someAccountIdentifier() = malaiTestFixturesContext.someAccountIdentifier

    @RenderedValue
    fun anotherAccountIdentifier() = malaiTestFixturesContext.anotherAccountIdentifier

    @RenderedValue
    fun someEmail() = malaiTestFixturesContext.someEmail

    @RenderedValue
    fun someName() = malaiTestFixturesContext.someName

    @RenderedValue
    fun someTransactionId() = malaiTestFixturesContext.someTransactionId

    @RenderedValue
    fun someInitiatedById() = malaiTestFixturesContext.someInitiatedById

    @RenderedValue
    fun someCounterpartyId() = malaiTestFixturesContext.someCounterpartyId

    companion object {
        @JvmField
        val malaiTestFixturesContext = MalaiTestFixturesContext()
    }

    class MalaiTestFixturesExtension : BeforeEachCallback, AfterEachCallback {
        private val boundByUs = ThreadLocal<Boolean>()

        override fun beforeEach(context: ExtensionContext) {
            if (!isRenderedValueContextBound()) {
                RenderedValueInvocationContextHolder.bindToCurrentThread(RenderedValueInvocationContext())
                boundByUs.set(true)
            }
            malaiTestFixturesContext.someAccountUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherAccountUid = UUID.randomUUID()
            malaiTestFixturesContext.someCurrency = "GBP"
            malaiTestFixturesContext.someAccessToken = "some-access-token"
            malaiTestFixturesContext.someSavingsGoalUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherSavingsGoalUid = UUID.randomUUID()
            malaiTestFixturesContext.someTransferUid = UUID.randomUUID()
            malaiTestFixturesContext.someUserId = UUID.randomUUID()
            malaiTestFixturesContext.anotherUserId = UUID.randomUUID()
            malaiTestFixturesContext.someDefaultCategory = UUID.randomUUID()
            malaiTestFixturesContext.anotherDefaultCategory = UUID.randomUUID()
            malaiTestFixturesContext.someCategoryUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherCategoryUid = UUID.randomUUID()
            malaiTestFixturesContext.someFeedItemUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherFeedItemUid = UUID.randomUUID()
            malaiTestFixturesContext.somePayeeUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherPayeeUid = UUID.randomUUID()
            malaiTestFixturesContext.somePayeeAccountUid = UUID.randomUUID()
            malaiTestFixturesContext.somePayeeName = "Bob Bobbington"
            malaiTestFixturesContext.someMandateUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherMandateUid = UUID.randomUUID()
            malaiTestFixturesContext.someOriginatorUid = UUID.randomUUID()
            malaiTestFixturesContext.someMerchantUid = UUID.randomUUID()
            malaiTestFixturesContext.someMandateReference = "MANDATE-001"
            malaiTestFixturesContext.someDirectDebitPaymentUid = UUID.randomUUID()
            malaiTestFixturesContext.someCardUid = UUID.randomUUID()
            malaiTestFixturesContext.someCardAssociationUid = UUID.randomUUID()
            malaiTestFixturesContext.somePaymentOrderUid = UUID.randomUUID()
            malaiTestFixturesContext.somePaymentUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherPaymentUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherPayeeAccountUid = UUID.randomUUID()
            malaiTestFixturesContext.someAccountHolderUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherAccountHolderUid = UUID.randomUUID()
            malaiTestFixturesContext.someIndividualUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherIndividualUid = UUID.randomUUID()
            malaiTestFixturesContext.someBusinessUid = UUID.randomUUID()
            malaiTestFixturesContext.someBusinessAddressUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherBusinessAddressUid = UUID.randomUUID()
            malaiTestFixturesContext.someSoleTraderUid = UUID.randomUUID()
            malaiTestFixturesContext.someProfileImageUid = UUID.randomUUID()
            malaiTestFixturesContext.someAddressUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherAddressUid = UUID.randomUUID()
            malaiTestFixturesContext.someTokenUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherTokenUid = UUID.randomUUID()
            malaiTestFixturesContext.someApplicationUserUid = UUID.randomUUID()
            malaiTestFixturesContext.someSpaceUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherSpaceUid = UUID.randomUUID()
            malaiTestFixturesContext.someSettleUpProfileUid = UUID.randomUUID()
            malaiTestFixturesContext.someFeedItemAttachmentUid = UUID.randomUUID()
            malaiTestFixturesContext.someReceiptUid = UUID.randomUUID()
            malaiTestFixturesContext.anotherReceiptUid = UUID.randomUUID()
            malaiTestFixturesContext.someMastercardFeedItemUid = UUID.randomUUID()
            malaiTestFixturesContext.somePayeeImageUid = UUID.randomUUID()
            malaiTestFixturesContext.someScheduledPaymentUid = UUID.randomUUID()
            malaiTestFixturesContext.someRecurringTransferUid = UUID.randomUUID()
            malaiTestFixturesContext.someRoundUpGoalUid = UUID.randomUUID()
            malaiTestFixturesContext.someCurrencyFlagUid = UUID.randomUUID()
            malaiTestFixturesContext.someSortCodeIdentifierId = UUID.randomUUID()
            malaiTestFixturesContext.someIbanBicIdentifierId = UUID.randomUUID()
            malaiTestFixturesContext.someBankIdentifier = "some-bank-id-${System.currentTimeMillis()}"
            malaiTestFixturesContext.anotherBankIdentifier = "another-bank-id-${System.currentTimeMillis()}"
            malaiTestFixturesContext.someAccountIdentifier = "some-account-id-${System.currentTimeMillis()}"
            malaiTestFixturesContext.anotherAccountIdentifier = "another-account-id-${System.currentTimeMillis()}"
            malaiTestFixturesContext.someEmail = "some-email-${UUID.randomUUID()}@example.com"
            malaiTestFixturesContext.someName = "John Mark"
            malaiTestFixturesContext.someTransactionId = UUID.randomUUID()
            malaiTestFixturesContext.someInitiatedById = UUID.randomUUID()
            malaiTestFixturesContext.someCounterpartyId = UUID.randomUUID()
        }

        override fun afterEach(context: ExtensionContext) {
            if (boundByUs.get() == true) {
                RenderedValueInvocationContextHolder.clearFromThread()
                boundByUs.remove()
            }
        }

        private fun isRenderedValueContextBound(): Boolean = try {
            RenderedValueInvocationContextHolder.renderedValueInvocationContext()
            true
        } catch (_: NullPointerException) {
            false
        }
    }

    class MalaiTestFixturesContext {
        lateinit var someAccountUid: UUID
        lateinit var anotherAccountUid: UUID
        lateinit var someCurrency: String
        lateinit var someAccessToken: String
        lateinit var someSavingsGoalUid: UUID
        lateinit var anotherSavingsGoalUid: UUID
        lateinit var someTransferUid: UUID
        lateinit var someUserId: UUID
        lateinit var anotherUserId: UUID
        lateinit var someDefaultCategory: UUID
        lateinit var anotherDefaultCategory: UUID
        lateinit var someCategoryUid: UUID
        lateinit var anotherCategoryUid: UUID
        lateinit var someFeedItemUid: UUID
        lateinit var anotherFeedItemUid: UUID
        lateinit var somePayeeUid: UUID
        lateinit var anotherPayeeUid: UUID
        lateinit var somePayeeAccountUid: UUID
        lateinit var somePayeeName: String
        lateinit var someMandateUid: UUID
        lateinit var anotherMandateUid: UUID
        lateinit var someOriginatorUid: UUID
        lateinit var someMerchantUid: UUID
        lateinit var someMandateReference: String
        lateinit var someDirectDebitPaymentUid: UUID
        lateinit var someCardUid: UUID
        lateinit var someCardAssociationUid: UUID
        lateinit var somePaymentOrderUid: UUID
        lateinit var somePaymentUid: UUID
        lateinit var anotherPaymentUid: UUID
        lateinit var anotherPayeeAccountUid: UUID
        lateinit var someAccountHolderUid: UUID
        lateinit var anotherAccountHolderUid: UUID
        lateinit var someIndividualUid: UUID
        lateinit var anotherIndividualUid: UUID
        lateinit var someBusinessUid: UUID
        lateinit var someBusinessAddressUid: UUID
        lateinit var anotherBusinessAddressUid: UUID
        lateinit var someSoleTraderUid: UUID
        lateinit var someProfileImageUid: UUID
        lateinit var someAddressUid: UUID
        lateinit var anotherAddressUid: UUID
        lateinit var someTokenUid: UUID
        lateinit var anotherTokenUid: UUID
        lateinit var someApplicationUserUid: UUID
        lateinit var someSpaceUid: UUID
        lateinit var anotherSpaceUid: UUID
        lateinit var someSettleUpProfileUid: UUID
        lateinit var someFeedItemAttachmentUid: UUID
        lateinit var someReceiptUid: UUID
        lateinit var anotherReceiptUid: UUID
        lateinit var someMastercardFeedItemUid: UUID
        lateinit var somePayeeImageUid: UUID
        lateinit var someScheduledPaymentUid: UUID
        lateinit var someRecurringTransferUid: UUID
        lateinit var someRoundUpGoalUid: UUID
        lateinit var someCurrencyFlagUid: UUID
        lateinit var someSortCodeIdentifierId: UUID
        lateinit var someIbanBicIdentifierId: UUID
        lateinit var someBankIdentifier: String
        lateinit var anotherBankIdentifier: String
        lateinit var someAccountIdentifier: String
        lateinit var anotherAccountIdentifier: String
        lateinit var someEmail: String
        lateinit var someName: String
        lateinit var someTransactionId: UUID
        lateinit var someInitiatedById: UUID
        lateinit var someCounterpartyId: UUID
    }
}
