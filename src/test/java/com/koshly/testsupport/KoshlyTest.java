package com.koshly.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole API against the throwaway test database. Use it instead of
 * {@code @SpringBootTest}. A meta-annotation: one annotation that stands for
 * several, so a new shared test setting is added here once.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public @interface KoshlyTest {
}
