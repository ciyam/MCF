package api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private static final List<Class<? extends Annotation>> HTTP_METHOD_ANNOTATIONS = Arrays.asList(
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
        
        // scan each resource class
        for (Class<?> resource : resources) {
            if(OpenApiResource.class.isAssignableFrom(resource))
                continue; // ignore swagger resources
            
            Path resourcePath = resource.getDeclaredAnnotation(Path.class);
            if(resourcePath == null)
                continue;
            
            String resourcePathString = resourcePath.value();
            
            // scan each method
            for(Method method : resource.getDeclaredMethods())
            {
                Operation operationAnnotation = method.getAnnotation(Operation.class);
                if(operationAnnotation == null)
                    continue;
                
                String description = operationAnnotation.description();
                
                Path methodPath = method.getDeclaredAnnotation(Path.class);
                String methodPathString = (methodPath != null) ? methodPath.value() : "";

                // scan for each potential http method
                for(Class<? extends Annotation> restMethodAnnotation : HTTP_METHOD_ANNOTATIONS)
                {
                    Annotation annotation = method.getDeclaredAnnotation(restMethodAnnotation);
                    if(annotation == null)
                        continue;

                    HttpMethod httpMethod = annotation.annotationType().getDeclaredAnnotation(HttpMethod.class);
                    String httpMethodString = httpMethod.value();

                    String fullPath = httpMethodString + " " + resourcePathString + methodPathString;
                    Pattern pattern = Pattern.compile("^ *(" + httpMethodString + " *)?" + getHelpPatternForPath(resourcePathString + methodPathString));
                    result.add(new HelpString(pattern, fullPath, description));
                }
            }
        }
        
        // sort by path
        result.sort((h1, h2)-> h1.fullPath.compareTo(h2.fullPath));
        
        return result;
    }
    
    private String getHelpPatternForPath(String path)
    {        
        path = path
            .replaceAll("\\.", "\\.")        // escapes "." as "\."
            .replaceAll("\\{.*?\\}", ".*?"); // replace placeholders "{...}" by the "ungreedy match anything" pattern ".*?"

        // arrange the regex pattern so that it also matches partial
        StringBuilder result = new StringBuilder();
        String[] parts = path.split("/");
        for(int i = 0; i < parts.length; i++)
        {
            if(i!=0)
                result.append("(/"); // opening bracket
            result.append(parts[i]);
        }
        for(int i = 0; i < parts.length - 1; i++)
            result.append(")?"); // closing bracket
        return result.toString();
    }
    
    public String executeCommand(String command)
    {
        final Matcher helpMatch = HELP_COMMAND_PATTERN.matcher(command);
        if(helpMatch.matches())
        {
            command = helpMatch.group("command");
            StringBuilder result = new StringBuilder();
         
            boolean showAll = command.trim().equalsIgnoreCase("all");
            for(HelpString helpString : helpStrings)
            {
                if(showAll || helpString.pattern.matcher(command).matches())
                    appendHelp(result, helpString);
            }
            
            return result.toString();
        }
        
        return null;
    }

    private void appendHelp(StringBuilder builder, HelpString helpString) {
        builder.append(helpString.fullPath + "\n");
        builder.append(helpString.description + "\n");
    }
}
