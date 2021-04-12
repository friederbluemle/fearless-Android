package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic

import jp.co.soramitsu.common.utils.hash
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Signature
import java.math.BigInteger

class TransferExtrinsic(
    val senderId: ByteArray,
    val recipientId: ByteArray,
    val amountInPlanks: BigInteger,
    val index: Pair<UByte, UByte>,
    val hash: String
)

class EncodeExtrinsicParams(
    val senderId: ByteArray,
    val recipientId: ByteArray,
    val amountInPlanks: BigInteger,
    val nonce: BigInteger,
    val runtimeVersion: RuntimeVersion,
    val networkType: Node.NetworkType,
    val encryptionType: EncryptionType,
    val genesis: ByteArray
)

class TransferExtrinsicFactory(
    private val signer: Signer
) {

    fun decode(scale: String): TransferExtrinsic? {
        return decodeV28(scale)
    }

    fun createEncodedExtrinsic(
        encodeExtrinsicParams: EncodeExtrinsicParams,
        keypair: Keypair
    ): String {
        return createExtrinsicV28(encodeExtrinsicParams, keypair)
    }

    private fun createExtrinsicV28(
        encodeExtrinsicParams: EncodeExtrinsicParams,
        keypair: Keypair
    ): String = with(encodeExtrinsicParams) {

        val callStruct = TransferCallV28 { call ->
            call[TransferCallV28.callIndex] = networkType.runtimeConfiguration.pallets.transfers.transfer.index

            call[TransferCallV28.args] = TransferArgsV28 { args ->
                args[TransferArgsV28.recipientId] = MultiAddress.Id(recipientId)
                args[TransferArgsV28.amount] = amountInPlanks
            }
        }

        val callBytes = TransferCallV28.toByteArray(callStruct)
        val payload = createExtrinsicPayloadValue(callBytes, encodeExtrinsicParams)

        val signatureValue = Signature(
            encryptionType = encryptionType,
            value = signer.signExtrinsic(payload, keypair, encryptionType)
        )

        val extrinsic = SignedExtrinsicV28 { extrinsic ->
            extrinsic[SignedExtrinsicV28.accountId] = MultiAddress.Id(senderId)
            extrinsic[SignedExtrinsicV28.signature] = signatureValue
            extrinsic[SignedExtrinsicV28.nonce] = nonce
            extrinsic[SignedExtrinsicV28.call] = callStruct
        }

        val extrinsicBytes = SignedExtrinsicV28.toByteArray(extrinsic)
        val byteLengthValue = extrinsicBytes.size.toBigInteger()

        val submittableExtrinsic = SubmittableExtrinsicV28 { struct ->
            struct[SubmittableExtrinsicV28.byteLength] = byteLengthValue
            struct[SubmittableExtrinsicV28.signedExtrinsic] = extrinsic
        }

        SubmittableExtrinsicV28.toHexString(submittableExtrinsic)
    }

    private fun createExtrinsicPayloadValue(
        callBytes: ByteArray,
        encodeExtrinsicParams: EncodeExtrinsicParams
    ) = with(encodeExtrinsicParams) {
        ExtrinsicPayloadValue { payload ->
            payload[ExtrinsicPayloadValue.call] = callBytes
            payload[ExtrinsicPayloadValue.nonce] = nonce
            payload[ExtrinsicPayloadValue.specVersion] = runtimeVersion.specVersion.toUInt()
            payload[ExtrinsicPayloadValue.transactionVersion] = runtimeVersion.transactionVersion.toUInt()

            payload[ExtrinsicPayloadValue.genesis] = genesis
            payload[ExtrinsicPayloadValue.blockHash] = genesis
        }
    }

    private fun decodeV28(scale: String): TransferExtrinsic? {
        val struct = SubmittableExtrinsicV28.readOrNull(scale) ?: return null

        val signedExtrinsic = struct[SubmittableExtrinsicV28.signedExtrinsic]
        val call = signedExtrinsic[SignedExtrinsicV28.call]
        val args = call[TransferCallV28.args]

        val senderId = signedExtrinsic[SignedExtrinsicV28.accountId] as? MultiAddress.Id ?: return null
        val recipientId = args[TransferArgsV28.recipientId] as? MultiAddress.Id ?: return null

        return TransferExtrinsic(
            senderId = senderId.value,
            recipientId = recipientId.value,
            amountInPlanks = args[TransferArgsV28.amount],
            index = call[TransferCallV28.callIndex],
            hash = struct.hash()
        )
    }
}