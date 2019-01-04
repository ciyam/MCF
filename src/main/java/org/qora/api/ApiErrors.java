package org.qora.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation lists potential ApiErrors that may be returned, or thrown, during the execution of this method.
 * <p>
 * Value is expected to be an array of ApiError enum instances.
 *
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {
	ApiError[] value() default {};
}
