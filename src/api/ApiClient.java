package api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PATCH;
import javax.ws.rs.DELETE;
import javax.ws.rs.HttpMethod;

public class ApiClient {
    private class HelpString
    {
        public final Pattern pattern;
        public final String fullPath;
        public final String description;
        
        public HelpString(Pattern pattern, String fullPath, String description)
        {
            this.pattern = pattern;
            this.fullPath = fullPath;
            this.description = description;
        }
    }
    
    private static final Pattern HELP_COMMAND_PATTERN = Pattern.compile("^ *help *(?<command>.*)$", Pattern.CASE_INSENSITIVE);
    private static final List<Class<? extends Annotation>> REST_METHOD_ANNOTATIONS = Arrays.asList(
        GET.class,
        POST.class,
        PUT.class,
        PATCH.class,
        DELETE.class
    );
    
    ApiService apiService;
    List<HelpString> helpStrings;
    
    public ApiClient(ApiService apiService)
    {
        this.apiService = apiService;        
        this.helpStrings = getHelpStrings(apiService.getResources());
    }

    private List<HelpString> getHelpStrings(Iterable<Class<?>> resources)
    {
        List<HelpString> result = new ArrayList<>();
        
        for (Class<?> resource : resources) {
            Path resourcePath = resource.getDeclaredAnnotation(Path.class);
            if(resourcePath == null)
                continue;
            
            String resourcePathString = resourcePath.value();
            
            for(Method method : resource.getDeclaredMethods())
            {
                UsageDescription usageDescription = method.getAnnotation(UsageDescription.class);
                if(usageDescription == null)
                    continue;
                
                String usageDescriptionString = usageDescription.value();
                
                Path methodPath = method.getDeclaredAnnotation(Path.class);
                String methodPathString = (methodPath != null) ? methodPath.value() : "";

                for(Class<? extends Annotation> restMethodAnnotation : REST_METHOD_ANNOTATIONS)
                {
                    Annotation annotation = method.getDeclaredAnnotation(restMethodAnnotation);
                    if(annotation == null)
                        continue;

                    HttpMethod httpMethod = annotation.annotationType().getDeclaredAnnotation(HttpMethod.class);
                    String httpMethodString = httpMethod.value();

                    Pattern pattern = Pattern.compile("^ *" + httpMethodString + " *" + getRegexPatternForPath(resourcePathString + methodPathString));
                    String fullPath = httpMethodString + " " + resourcePathString + methodPathString;
                    result.add(new HelpString(pattern, fullPath, usageDescriptionString));
                }
            }
        }
        
        return result;
    }
    
    private String getRegexPatternForPath(String path)
    {
        return path
            .replaceAll("\\.", "\\.")       // escapes "." as "\."
            .replaceAll("\\{.*?\\}", ".*?");  // replace placeholders "{...}" by the "ungreedy match anything" pattern ".*?"
    }
    
    public String executeCommand(String command)
    {
        final Matcher helpMatch = HELP_COMMAND_PATTERN.matcher(command);
        if(helpMatch.matches())
        {
            command = helpMatch.group("command");
            StringBuilder help = new StringBuilder();
            
            for(HelpString helpString : helpStrings)
            {
                if(helpString.pattern.matcher(command).matches())
                {
                    help.append(helpString.fullPath + "\n");
                    help.append(helpString.description + "\n");
                }
            }
            
            return help.toString();
        }
        
        return null;
    }
}
