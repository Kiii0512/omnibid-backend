package com.omnibid.support;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(RepeatedConcurrencyTest.ConcurrencyRepeatExtension.class)
public @interface RepeatedConcurrencyTest {

    class ConcurrencyRepeatExtension implements TestTemplateInvocationContextProvider {

        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            int repeats = Integer.getInteger("concurrency.repeats", 3);
            return IntStream.rangeClosed(1, repeats).mapToObj(i -> new TestTemplateInvocationContext() {
                @Override
                public String getDisplayName(int invocationIndex) {
                    return "repetition " + invocationIndex + " of " + repeats;
                }
            });
        }
    }
}
