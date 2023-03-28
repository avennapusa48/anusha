package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.payments.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString


@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LightningOutgoingClosingTxPartWrapper(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @ByteString val txId: ByteArray,
    val sat: Long,
    val info: ClosingInfoWrapper,
    val createdAt: Long
) {

    constructor(part: LightningOutgoingPayment.ClosingTxPart) : this(
        id = part.id,
        txId = part.txId.toByteArray(),
        sat = part.claimed.sat,
        info = ClosingInfoWrapper(part),
        createdAt = part.createdAt
    )

    fun unwrap() = LightningOutgoingPayment.ClosingTxPart(
        id = id,
        txId = txId.toByteVector32(),
        claimed = Satoshi(sat),
        closingType = info.unwrap(),
        createdAt = createdAt
    )

    /** Wrapper for the closing info data object. */
    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class ClosingInfoWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(txPart: LightningOutgoingPayment.ClosingTxPart): ClosingInfoWrapper {
                val (type, blob) = txPart.mapClosingTypeToDb()
                return ClosingInfoWrapper(
                    type = type.name,
                    blob = blob
                )
            }
        }

        fun unwrap(): ChannelClosingType {
            return OutgoingPartClosingInfoData.deserialize(
                typeVersion = OutgoingPartClosingInfoTypeVersion.valueOf(type),
                blob = blob
            )
        }
    }
}

@Serializable
data class LightningOutgoingPartWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val msat: Long,
    val route: String,
    val status: StatusWrapper?,
    val createdAt: Long
) {
    constructor(part: LightningOutgoingPayment.LightningPart) : this(
        id = part.id,
        msat = part.amount.msat,
        route = OutgoingQueries.hopDescAdapter.encode(part.route),
        status = StatusWrapper(part.status),
        createdAt = part.createdAt
    )

    fun unwrap() = LightningOutgoingPayment.LightningPart(
        id = id,
        amount = MilliSatoshi(msat = msat),
        route = OutgoingQueries.hopDescAdapter.decode(route),
        status = status?.unwrap() ?: LightningOutgoingPayment.LightningPart.Status.Pending,
        createdAt = createdAt
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class StatusWrapper(
        val ts: Long, // timestamp: completedAt
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(status: LightningOutgoingPayment.LightningPart.Status): StatusWrapper? {
                return when (status) {
                    is LightningOutgoingPayment.LightningPart.Status.Pending -> null
                    is LightningOutgoingPayment.LightningPart.Status.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is LightningOutgoingPayment.LightningPart.Status.Succeeded -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                }
            }
        } // </companion object>

        fun unwrap(): LightningOutgoingPayment.LightningPart.Status {
            return OutgoingPartStatusData.deserialize(
                typeVersion = OutgoingPartStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }
    } // </StatusWrapper>

} // </OutgoingPartData>