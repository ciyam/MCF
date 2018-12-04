package api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
		info = @Info( title = "Qora API", description = "NOTE: byte-arrays currently returned as Base64 but this is likely to change to Base58" ),
		tags = {
			@Tag(name = "addresses"),
			@Tag(name = "admin"),
			@Tag(name = "blocks"),
			@Tag(name = "transactions")
		},
		extensions = {
			@Extension(name = "translation", properties = {
					@ExtensionProperty(name="title.key", value="info:title")
			})
		}
)
public class ApiDefinition {

}
