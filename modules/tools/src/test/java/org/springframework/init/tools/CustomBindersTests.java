/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.init.tools;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomBindersTests {

	@BeforeAll
	static public void init() {
		System.setProperty("spring.init.custom-binders", "true");
	}

	@AfterAll
	static public void close() {
		System.clearProperty("spring.init.custom-binders");
	}

	@Test
	public void scanSubPackage() {
		TypeSpec.Builder type = TypeSpec.classBuilder("Generated");
		type.addMethods(new CustomBinderBuilder().getBinders());
		JavaFile file = JavaFile.builder("app.main", type.build()).build();
		// System.err.println(file);
		assertThat(file.toString())
				.contains("MessageSourceProperties messageSourceProperties(MessageSourceProperties bean,");
	}

}
