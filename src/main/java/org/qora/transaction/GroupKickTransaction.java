package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;

public class GroupKickTransaction extends Transaction {

	// Properties
	private GroupKickTransactionData groupKickTransactionData;

	// Constructors

	public GroupKickTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.groupKickTransactionData = (GroupKickTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getAdmin().getAddress()))
			return true;

		if (address.equals(this.getMember().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getAdmin().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getAdmin() throws DataException {
		return new PublicKeyAccount(this.repository, this.groupKickTransactionData.getAdminPublicKey());
	}

	public Account getMember() throws DataException {
		return new Account(this.repository, this.groupKickTransactionData.getMember());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check member address is valid
		if (!Crypto.isValidAddress(groupKickTransactionData.getMember()))
			return ValidationResult.INVALID_ADDRESS;

		GroupRepository groupRepository = this.repository.getGroupRepository();
		int groupId = groupKickTransactionData.getGroupId();
		GroupData groupData = groupRepository.fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Can't kick if not an admin
		if (!groupRepository.adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account member = getMember();

		// Check member actually in group UNLESS there's a pending join request
		if (!groupRepository.joinRequestExists(groupId, member.getAddress()) && !groupRepository.memberExists(groupId, member.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Can't kick another admin unless the group owner
		if (!admin.getAddress().equals(groupData.getOwner()) && groupRepository.adminExists(groupId, member.getAddress()))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Check fee is positive
		if (groupKickTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(groupKickTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, groupKickTransactionData.getGroupId());
		group.kick(groupKickTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(groupKickTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, groupKickTransactionData.getGroupId());
		group.unkick(groupKickTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(groupKickTransactionData);
	}

}
