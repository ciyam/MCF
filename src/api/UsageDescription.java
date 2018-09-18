package api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.TYPE,ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
@Documented
public abstract @interface UsageDescription {
    public abstract String value();
}