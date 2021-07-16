package com.k8s.dummy.operator;

import com.k8s.dummy.operator.controller.DummyOperator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class OperatorApplicationTests {

	@MockBean
	private DummyOperator xGeeksController;

	@MockBean
	private OperatorConfigs configs;

	@Test
	void contextLoads() {
	}

}
