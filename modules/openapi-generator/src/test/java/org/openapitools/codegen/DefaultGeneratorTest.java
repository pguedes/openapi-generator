package org.openapitools.codegen;

import io.swagger.models.Response;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.openapitools.codegen.utils.ModelUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DefaultGeneratorTest {

    @Test
    public void testProcessPaths() throws Exception {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        openAPI.setPaths(new Paths());
        openAPI.getPaths().addPathItem("/path1", new PathItem().get(new Operation().operationId("op1").responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));
        openAPI.getPaths().addPathItem("/path2", new PathItem().get(new Operation().operationId("op2").addParametersItem(new QueryParameter().name("p1").schema(new StringSchema())).responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));
        openAPI.getPaths().addPathItem("/path3", new PathItem().addParametersItem(new QueryParameter().name("p1").schema(new StringSchema())).get(new Operation().operationId("op3").addParametersItem(new QueryParameter().name("p2").schema(new IntegerSchema())).responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));
        openAPI.getPaths().addPathItem("/path4", new PathItem().addParametersItem(new QueryParameter().name("p1").schema(new StringSchema())).get(new Operation().operationId("op4").responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));

        ClientOptInput opts = new ClientOptInput();
        opts.setOpenAPI(openAPI);
        opts.setConfig(new DefaultCodegen());

        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(opts);
        Map<String, List<CodegenOperation>> result = generator.processPaths(openAPI.getPaths());
        Assert.assertEquals(result.size(), 1);
        List<CodegenOperation> defaultList = result.get("Default");
        Assert.assertEquals(defaultList.size(), 4);
        Assert.assertEquals(defaultList.get(0).path, "/path1");
        Assert.assertEquals(defaultList.get(0).allParams.size(), 0);
        Assert.assertEquals(defaultList.get(1).path, "/path2");
        Assert.assertEquals(defaultList.get(1).allParams.size(), 1);
        Assert.assertEquals(defaultList.get(2).path, "/path3");
        Assert.assertEquals(defaultList.get(2).allParams.size(), 2);
        Assert.assertEquals(defaultList.get(3).path, "/path4");
        Assert.assertEquals(defaultList.get(3).allParams.size(), 1);
    }


    @Test
    public void testNonStrictProcessPaths() throws Exception {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        openAPI.setPaths(new Paths());
        openAPI.getPaths().addPathItem("path1/", new PathItem().get(new Operation().operationId("op1").responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));
        openAPI.getPaths().addPathItem("path2/", new PathItem().get(new Operation().operationId("op2").addParametersItem(new QueryParameter().name("p1").schema(new StringSchema())).responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")))));

        ClientOptInput opts = new ClientOptInput();
        opts.setOpenAPI(openAPI);
        CodegenConfig config = new DefaultCodegen();
        config.setStrictSpecBehavior(false);
        opts.setConfig(config);

        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(opts);
        Map<String, List<CodegenOperation>> result = generator.processPaths(openAPI.getPaths());
        Assert.assertEquals(result.size(), 1);
        List<CodegenOperation> defaultList = result.get("Default");
        Assert.assertEquals(defaultList.size(), 2);
        Assert.assertEquals(defaultList.get(0).path, "path1/");
        Assert.assertEquals(defaultList.get(0).allParams.size(), 0);
        Assert.assertEquals(defaultList.get(1).path, "path2/");
        Assert.assertEquals(defaultList.get(1).allParams.size(), 1);
    }

    @Test
    public void testRefModelValidationProperties(){
        OpenAPI openAPI = TestUtils.parseSpec("src/test/resources/2_0/refAliasedPrimitiveWithValidation.yml");
        ClientOptInput opts = new ClientOptInput();
        opts.setOpenAPI(openAPI);
        DefaultCodegen config = new DefaultCodegen();
        config.setStrictSpecBehavior(false);
        opts.setConfig(config);

        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(opts);

        String expectedPattern = "^\\d{3}-\\d{2}-\\d{4}$";
        // NOTE: double-escaped regex for when the value is intended to be dumped in template into a String location.
        String escapedPattern = config.toRegularExpression(expectedPattern);

        Schema stringRegex = openAPI.getComponents().getSchemas().get("StringRegex");
        // Sanity check.
        Assert.assertEquals(stringRegex.getPattern(), expectedPattern);

        // Validate when we alias/unalias
        Schema unaliasedStringRegex = ModelUtils.unaliasSchema(openAPI, stringRegex);
        Assert.assertEquals(unaliasedStringRegex.getPattern(), expectedPattern);

        // Validate when converting to property
        CodegenProperty stringRegexProperty = config.fromProperty("stringRegex", stringRegex);
        Assert.assertEquals(stringRegexProperty.pattern, escapedPattern);

        // Validate when converting to parameter
        Operation operation = openAPI.getPaths().get("/fake/StringRegex").getPost();
        RequestBody body = operation.getRequestBody();
        CodegenParameter codegenParameter = config.fromRequestBody(body, new HashSet<>(), "body");

        Assert.assertEquals(codegenParameter.pattern, escapedPattern);

        // Validate when converting to response
        ApiResponse response = operation.getResponses().get("200");
        CodegenResponse codegenResponse = config.fromResponse("200", response);

        Assert.assertEquals(((Schema)codegenResponse.schema).getPattern(), expectedPattern);
        Assert.assertEquals(codegenResponse.pattern, escapedPattern);
    }

    @Test
    public void minimalUpdateTest() throws IOException {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        ClientOptInput opts = new ClientOptInput();
        opts.setOpenAPI(openAPI);
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setEnableMinimalUpdate(true);
        opts.setConfig(codegen);
        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(opts);
        File testPath = new File("temp/overwrite.test");
        if (testPath.exists()) {
            testPath.delete();
        }
        generator.writeToFile(testPath.toString(), "some file contents");
        long createTime = testPath.lastModified();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
        }
        generator.writeToFile(testPath.toString(), "some file contents");
        Assert.assertEquals(createTime, testPath.lastModified());
        File testPathTmp = new File("temp/overwrite.test.tmp");
        Assert.assertFalse(testPathTmp.exists());
        testPath.delete();
    }
}
