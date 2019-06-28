package api.resource;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
		info = @Info( title = "MCF API", description = "NOTE: byte-arrays are encoded in Base58" ),
		tags = {
			@Tag(name = "Addresses"),
			@Tag(name = "Admin"),
			@Tag(name = "Assets"),
			@Tag(name = "Blocks"),
			@Tag(name = "Names"),
			@Tag(name = "Payments"),
			@Tag(name = "Transactions"),
			@Tag(name = "Utilities")
		},
		extensions = {
			@Extension(name = "translation", properties = {
					@ExtensionProperty(name="title.key", value="info:title")
			})
		}
)
public class ApiDefinition {
}