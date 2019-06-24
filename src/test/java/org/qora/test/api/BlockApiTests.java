package org.qora.test.api;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.qora.api.resource.BlocksResource;
import org.qora.test.common.ApiCommon;

public class BlockApiTests extends ApiCommon {

	private BlocksResource blocksResource;

	@Before
	public void buildResource() {
		this.blocksResource = (BlocksResource) ApiCommon.buildResource(BlocksResource.class);
	}

	@Test
	public void test() {
		assertNotNull(this.blocksResource);
	}

	@Test
	public void testGetBlockForgers() {
		List<String> addresses = Arrays.asList(aliceAddress, aliceAddress);

		assertNotNull(this.blocksResource.getBlockForgers(Collections.emptyList(), null, null, null));
		assertNotNull(this.blocksResource.getBlockForgers(addresses, null, null, null));
		assertNotNull(this.blocksResource.getBlockForgers(Collections.emptyList(), 1, 1, true));
		assertNotNull(this.blocksResource.getBlockForgers(addresses, 1, 1, true));
	}

	@Test
	public void testGetBlocksByForger() {
		assertNotNull(this.blocksResource.getBlocksByForger(aliceAddress, null, null, null));
		assertNotNull(this.blocksResource.getBlocksByForger(aliceAddress, 1, 1, true));
	}

}
