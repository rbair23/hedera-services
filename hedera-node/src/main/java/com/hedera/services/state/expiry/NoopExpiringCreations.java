package com.hedera.services.state.expiry;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.time.Instant;
import java.util.List;

public enum NoopExpiringCreations implements EntityCreator {
	NOOP_EXPIRING_CREATIONS;

	@Override
	public void setLedger(final HederaLedger ledger) {
		/* No-op */
	}

	@Override
	public ExpirableTxnRecord saveExpiringRecord(
			AccountID id, ExpirableTxnRecord expiringRecord, long consensusTime, long submittingMember) {
		throw new UnsupportedOperationException();
	}


	@Override
	public ExpirableTxnRecord.Builder createTopLevelRecord(long fee, byte[] hash, TxnAccessor accessor,
			Instant consensusTime, TxnReceipt.Builder receiptBuilder, List<FcAssessedCustomFee> assessedCustomFees,
			SideEffectsTracker sideEffectsTracker) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExpirableTxnRecord.Builder createSuccessfulSyntheticRecord(List<FcAssessedCustomFee> assessedCustomFees,
			SideEffectsTracker sideEffectsTracker, final String memo) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExpirableTxnRecord.Builder createUnsuccessfulSyntheticRecord(ResponseCodeEnum failureReason) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExpirableTxnRecord.Builder createInvalidFailureRecord(TxnAccessor accessor, Instant consensusTimestamp) {
		throw new UnsupportedOperationException();
	}
}