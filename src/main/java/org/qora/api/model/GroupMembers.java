package org.qora.api.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Group info, maybe including members")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupMembers {

	Integer memberCount;
	Integer adminCount;

	@XmlAccessorType(XmlAccessType.FIELD)
	@Schema(description = "Member info")
	public static class MemberInfo {
		public String member;
		public Long joined;
		public Boolean isAdmin;

		// For JAX-RS
		protected MemberInfo() {
		}

		public MemberInfo(String member, Long joined, boolean isAdmin) {
			this.member = member;
			this.joined = joined;
			this.isAdmin = isAdmin ? true : null; // null so field is not displayed by API
		}
	}

	@XmlElement(name = "members")
	public List<MemberInfo> groupMembers;

	// For JAX-RS
	protected GroupMembers() {
	}

	public GroupMembers(List<MemberInfo> groupMembers, Integer memberCount, Integer adminCount) {
		this.groupMembers = groupMembers;
		this.memberCount = memberCount;
		this.adminCount = adminCount;
	}

}
