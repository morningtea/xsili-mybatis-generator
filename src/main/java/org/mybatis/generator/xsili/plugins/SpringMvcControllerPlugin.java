package org.mybatis.generator.xsili.plugins;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.JavaVisibility;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.Parameter;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.plugins.PluginUtils;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Spring MVC Controller 代码生成插件
 * 
 * @since 2.0.0
 * @author 叶鹏
 * @date 2017年8月26日
 */
public class SpringMvcControllerPlugin extends PluginAdapter {

    private boolean isRestful = true;
    private boolean enableSwaggerAnnotation = true;
    private FullyQualifiedJavaType abstractControllerType;
    private FullyQualifiedJavaType resultModelType;
    private FullyQualifiedJavaType validatorUtilType;
    private FullyQualifiedJavaType pagerType;

    private FullyQualifiedJavaType controllerType;
    private FullyQualifiedJavaType serviceType;
    private FullyQualifiedJavaType modelType;
    /**
     * 如果有WithBlob, 则modelWithBLOBsType赋值为BlobModel, 否则赋值为BaseModel
     */
    private FullyQualifiedJavaType modelWithBLOBsType;

    private String targetPackage;
    private String targetPackageService;
    private String project;
    private String modelPackage;

    private FullyQualifiedJavaType annotationResource;
    private FullyQualifiedJavaType annotationController;
    private FullyQualifiedJavaType annotationRequestMapping;
    private FullyQualifiedJavaType annotationRequestMethod;
    private FullyQualifiedJavaType annotationPathVariable;
    private FullyQualifiedJavaType annotationRequestParam;
    private FullyQualifiedJavaType annotationApiOperation;

    public SpringMvcControllerPlugin() {
        super();
    }

    @Override
    public boolean validate(List<String> warnings) {
        String isRestfulStr = properties.getProperty("isRestful");
        if (StringUtils.isNotBlank(isRestfulStr)) {
            isRestful = Boolean.parseBoolean(isRestfulStr);
        }
        String enableSwaggerAnnotationStr = properties.getProperty("enableSwaggerAnnotation");
        if (StringUtils.isNotBlank(enableSwaggerAnnotationStr)) {
            enableSwaggerAnnotation = Boolean.parseBoolean(enableSwaggerAnnotationStr);
        }

        String abstractController = properties.getProperty("abstractController");
        if (StringUtils.isBlank(abstractController)) {
            throw new RuntimeException("property abstractController is null");
        } else {
            abstractControllerType = new FullyQualifiedJavaType(abstractController);
        }

        String resultModel = properties.getProperty("resultModel");
        if (StringUtils.isBlank(resultModel)) {
            throw new IllegalArgumentException("property resultModel is null");
        }
        resultModelType = new FullyQualifiedJavaType(resultModel);

        String pager = properties.getProperty("pager");
        if (StringUtils.isBlank(pager)) {
            throw new RuntimeException("property pager is null");
        } else {
            pagerType = new FullyQualifiedJavaType(pager);
        }

        String validatorUtil = properties.getProperty("validatorUtil");
        if (StringUtils.isNotBlank(validatorUtil)) {
            validatorUtilType = new FullyQualifiedJavaType(validatorUtil);
        }

        targetPackage = properties.getProperty("targetPackage");
        targetPackageService = properties.getProperty("targetPackageService");
        project = properties.getProperty("targetProject");
        modelPackage = context.getJavaModelGeneratorConfiguration().getTargetPackage();

        annotationResource = new FullyQualifiedJavaType("javax.annotation.Resource");
        annotationController = new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RestController");
        annotationRequestMapping = new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RequestMapping");
        annotationRequestMethod = new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RequestMethod");
        annotationPathVariable = new FullyQualifiedJavaType("org.springframework.web.bind.annotation.PathVariable");
        annotationRequestParam = new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RequestParam");
        annotationApiOperation = new FullyQualifiedJavaType("io.swagger.annotations.ApiOperation");

        return true;
    }

    @Override
    public List<GeneratedJavaFile> contextGenerateAdditionalJavaFiles(IntrospectedTable introspectedTable) {
        String table = introspectedTable.getBaseRecordType();
        String tableName = table.replaceAll(this.modelPackage + ".", "");

        modelWithBLOBsType = modelType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        if (introspectedTable.getRules().generateRecordWithBLOBsClass()) {
            modelWithBLOBsType = new FullyQualifiedJavaType(introspectedTable.getRecordWithBLOBsType());
        }
        serviceType = new FullyQualifiedJavaType(targetPackageService + "." + tableName + "Service");
        controllerType = new FullyQualifiedJavaType(targetPackage + "." + tableName + "Controller");

        TopLevelClass topLevelClass = new TopLevelClass(controllerType);
        // 导入必要的类
        addImport(topLevelClass, introspectedTable);
        // 实现类
        List<GeneratedJavaFile> files = new ArrayList<GeneratedJavaFile>();
        addController(topLevelClass, introspectedTable, files);

        return files;
    }

    /**
     * 生成Controller
     * 
     * @param topLevelClass
     * @param introspectedTable
     * @param files
     */
    private void addController(TopLevelClass topLevelClass,
                               IntrospectedTable introspectedTable,
                               List<GeneratedJavaFile> files) {
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        // 父类
        if (abstractControllerType != null) {
            topLevelClass.setSuperClass(abstractControllerType);
        }
        // 添加注解
        topLevelClass.addAnnotation("@RestController");
        topLevelClass.addAnnotation("@RequestMapping(\"/v1/" + PluginUtils.humpToEnDash(modelType.getShortName())
                                    + "/\")");

        // 添加 Mapper引用
        addServiceField(topLevelClass);

        // 添加基础方法

        Method addMethod = createEntity(topLevelClass, introspectedTable);
        topLevelClass.addMethod(addMethod);

        Method updateMethod = updateEntity(topLevelClass, introspectedTable, false);
        topLevelClass.addMethod(updateMethod);

        Method updateSelectiveMethod = updateEntity(topLevelClass, introspectedTable, true);
        topLevelClass.addMethod(updateSelectiveMethod);

        Method deleteMethod = deleteEntity(introspectedTable);
        topLevelClass.addMethod(deleteMethod);

        Method getMethod = getEntity(introspectedTable);
        topLevelClass.addMethod(getMethod);

        Method listMethod = listEntitys(topLevelClass, introspectedTable);
        topLevelClass.addMethod(listMethod);

        // 生成文件
        GeneratedJavaFile file = new GeneratedJavaFile(topLevelClass, project, context.getProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING), context.getJavaFormatter());
        files.add(file);
    }

    /**
     * 导入需要的类
     */
    private void addImport(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        // 导入key类型
        List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
        for (Parameter keyParameter : keyParameterList) {
            if (keyParameter.getName().equals(PluginUtils.PRIMARY_KEY_PARAMETER_NAME)) {
                topLevelClass.addImportedType(keyParameter.getType());
            }
        }

        topLevelClass.addImportedType(pagerType);
        if (abstractControllerType != null) {
            topLevelClass.addImportedType(abstractControllerType);
        }
        if (validatorUtilType != null) {
            topLevelClass.addImportedType(validatorUtilType);
        }

        topLevelClass.addImportedType(resultModelType);
        topLevelClass.addImportedType(serviceType);
        topLevelClass.addImportedType(modelType);
        topLevelClass.addImportedType(modelWithBLOBsType);

        topLevelClass.addImportedType(annotationResource);
        topLevelClass.addImportedType(annotationController);
        topLevelClass.addImportedType(annotationRequestMapping);
        topLevelClass.addImportedType(annotationRequestMethod);
        topLevelClass.addImportedType(annotationRequestParam);
        if (isRestful) {
            topLevelClass.addImportedType(annotationPathVariable);
        }
        if (enableSwaggerAnnotation) {
            topLevelClass.addImportedType(annotationApiOperation);
        }
    }

    /**
     * add
     * 
     * @param topLevelClass
     * @param introspectedTable
     * @return
     */
    private Method createEntity(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        String modelParamName = PluginUtils.getTypeParamName(modelWithBLOBsType);

        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("create");
        method.setReturnType(resultModelType);
        if (this.isRestful) {
            method.addAnnotation(getRestfulRequestMappingAnnotation(null, RequestMethod.POST));
        } else {
            method.addAnnotation("@RequestMapping(value = \"/create\", method = RequestMethod.POST)");
        }

        // swagger
        if (enableSwaggerAnnotation) {
            method.addAnnotation("@ApiOperation(value = \"新增\", notes = \"\", response = "
                                 + resultModelType.getShortName() + ".class)");
        }

        // 添加方法参数
        List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
        List<IntrospectedColumn> introspectedColumns = introspectedTable.getAllColumns();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            boolean isPrimaryKey = primaryKeyColumns.contains(introspectedColumn);
            String javaProperty = introspectedColumn.getJavaProperty();
            // 排除主键, 创建时间, 更新时间
            if (!isPrimaryKey && !"createTime".equals(javaProperty) && !"updateTime".equals(javaProperty)) {
                Parameter parameter = new Parameter(introspectedColumn.getFullyQualifiedJavaType(), javaProperty);
                parameter.addAnnotation("@RequestParam(required = false)");
                method.addParameter(parameter);
            }
        }

        // 填充参数
        method.addBodyLine("// 填充参数");
        StringBuilder sb = new StringBuilder();
        sb.append(modelWithBLOBsType.getShortName() + " " + modelParamName);
        sb.append(" = ");
        sb.append("new ").append(modelWithBLOBsType.getShortName()).append("();");
        method.addBodyLine(sb.toString());
        PluginUtils.generateModelSetterBodyLine(modelParamName, method);

        if (validatorUtilType != null) {
            method.addBodyLine("// 校验参数");
            method.addBodyLine("ValidatorUtil.checkParams(" + modelParamName + ");");
        }

        method.addBodyLine("");
        method.addBodyLine(modelParamName + " = this." + getService() + "create(" + modelParamName + ");");
        method.addBodyLine("return super.success(" + modelParamName + ");");

        return method;
    }

    /**
     * update
     * 
     * @param topLevelClass
     * @param introspectedTable
     * @return
     */
    private Method updateEntity(TopLevelClass topLevelClass, IntrospectedTable introspectedTable, boolean isSelective) {
        String modelParamName = PluginUtils.getTypeParamName(modelWithBLOBsType);

        List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();

        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        if (isSelective) {
            method.setName("updateSelective");
        } else {
            method.setName("update");
        }
        method.setReturnType(resultModelType);
        if (this.isRestful) {
            if (isSelective) {
                method.addAnnotation(getRestfulRequestMappingAnnotation(primaryKeyColumns, RequestMethod.PATCH));
            } else {
                method.addAnnotation(getRestfulRequestMappingAnnotation(primaryKeyColumns, RequestMethod.PUT));
            }
        } else {
            method.addAnnotation("@RequestMapping(value = \"/update\", method = RequestMethod.POST)");
        }

        // swagger
        if (enableSwaggerAnnotation) {
            String doc = isSelective ? "更新部分字段" : "更新全部字段";
            method.addAnnotation("@ApiOperation(value = \"" + doc + "\", notes = \"\", response = "
                                 + resultModelType.getShortName() + ".class)");
        }

        // 添加方法参数(主键)
        addKeyParameters(method, primaryKeyColumns);
        // 添加方法参数
        List<IntrospectedColumn> introspectedColumns = introspectedTable.getAllColumns();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            String javaProperty = introspectedColumn.getJavaProperty();
            boolean isPrimaryKey = primaryKeyColumns.contains(introspectedColumn);
            if (!isPrimaryKey && !"createTime".equals(javaProperty) && !"updateTime".equals(javaProperty)) {
                Parameter parameter = new Parameter(introspectedColumn.getFullyQualifiedJavaType(), javaProperty);
                parameter.addAnnotation("@RequestParam(required = false)");
                method.addParameter(parameter);
            }
        }

        // 填充参数
        method.addBodyLine("// 填充参数");
        StringBuilder sb = new StringBuilder();
        sb.append(modelWithBLOBsType.getShortName() + " " + modelParamName);
        sb.append(" = ");
        sb.append("new ").append(modelWithBLOBsType.getShortName()).append("();");
        method.addBodyLine(sb.toString());
        PluginUtils.generateModelSetterBodyLine(modelParamName, method);

        // 校验参数
        if (validatorUtilType != null) {
            method.addBodyLine("// 校验参数");
            method.addBodyLine("ValidatorUtil.checkParams(" + modelParamName + ");");
        }

        // 调用Service
        method.addBodyLine("");
        if (isSelective) {
            method.addBodyLine(modelParamName + " = this." + getService() + "update(" + modelParamName + ");");
        } else {
            method.addBodyLine(modelParamName + " = this." + getService() + "updateSelective(" + modelParamName + ");");
        }
        method.addBodyLine("return super.success(" + modelParamName + ");");

        return method;
    }

    /**
     * delete
     * 
     * @param introspectedTable
     * @return
     */
    private Method deleteEntity(IntrospectedTable introspectedTable) {
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("delete");
        method.setReturnType(resultModelType);

        List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
        if (this.isRestful) {
            method.addAnnotation(getRestfulRequestMappingAnnotation(primaryKeyColumns, RequestMethod.DELETE));
        } else {
            method.addAnnotation("@RequestMapping(value = \"/delete\", method = RequestMethod.DELETE)");
        }

        // swagger
        if (enableSwaggerAnnotation) {
            method.addAnnotation("@ApiOperation(value = \"删除\", notes = \"\", response = "
                                 + resultModelType.getShortName() + ".class)");
        }

        // 添加方法参数(主键)
        addKeyParameters(method, primaryKeyColumns);

        // 构造Service调用参数
        String params = "";
        String keyModelParamName = PluginUtils.PRIMARY_KEY_PARAMETER_NAME;
        if (introspectedTable.getRules().generatePrimaryKeyClass()) {
            FullyQualifiedJavaType keyModeltype = new FullyQualifiedJavaType(introspectedTable.getPrimaryKeyType());
            // 填充key参数
            StringBuilder sb = new StringBuilder();
            sb.append(keyModeltype.getShortName() + " " + keyModelParamName);
            sb.append(" = ");
            sb.append("new ").append(keyModeltype.getShortName()).append("();");
            method.addBodyLine(sb.toString());
            PluginUtils.generateModelSetterBodyLine(keyModelParamName, method);
            // call param
            params = keyModelParamName;
        } else {
            List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
            params = PluginUtils.getCallParameters(keyParameterList);
        }

        // 调用Service
        method.addBodyLine("boolean successful = this." + getService() + "delete(" + params + ");");
        method.addBodyLine("if (!successful) {");
        method.addBodyLine("return super.error(\"记录不存在或已被删除\");");
        method.addBodyLine("} else {");
        method.addBodyLine("return super.success();");
        method.addBodyLine("}");
        return method;
    }

    /**
     * get
     * 
     * @param introspectedTable
     * @return
     */
    private Method getEntity(IntrospectedTable introspectedTable) {
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("get");
        method.setReturnType(resultModelType);

        List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
        if (this.isRestful) {
            method.addAnnotation(getRestfulRequestMappingAnnotation(primaryKeyColumns, RequestMethod.GET));
        } else {
            method.addAnnotation("@RequestMapping(value = \"/get\", method = RequestMethod.GET)");
        }

        // swagger
        if (enableSwaggerAnnotation) {
            method.addAnnotation("@ApiOperation(value = \"详情\", notes = \"\", response = "
                                 + resultModelType.getShortName() + ".class)");
        }

        // 添加方法参数(主键)
        addKeyParameters(method, primaryKeyColumns);

        // 构造Service调用参数
        String params = "";
        String keyModelParamName = PluginUtils.PRIMARY_KEY_PARAMETER_NAME;
        if (introspectedTable.getRules().generatePrimaryKeyClass()) {
            FullyQualifiedJavaType keyModeltype = new FullyQualifiedJavaType(introspectedTable.getPrimaryKeyType());
            // 填充key参数
            StringBuilder sb = new StringBuilder();
            sb.append(keyModeltype.getShortName() + " " + keyModelParamName);
            sb.append(" = ");
            sb.append("new ").append(keyModeltype.getShortName()).append("();");
            method.addBodyLine(sb.toString());
            PluginUtils.generateModelSetterBodyLine(keyModelParamName, method);
            // call param
            params = keyModelParamName;
        } else {
            List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
            params = PluginUtils.getCallParameters(keyParameterList);
        }

        // 调用Service
        String modelParamName = PluginUtils.getTypeParamName(modelWithBLOBsType);
        StringBuilder sb = new StringBuilder();
        sb.append(modelWithBLOBsType.getShortName() + " " + modelParamName);
        sb.append(" = ");
        sb.append("this.").append(getService()).append("get").append("(").append(params).append(");");
        method.addBodyLine(sb.toString());
        method.addBodyLine("if(" + modelParamName + " == null){");
        method.addBodyLine("return super.error(\"记录不存在\");");
        method.addBodyLine("}");
        method.addBodyLine("return super.success(" + modelParamName + ");");
        return method;
    }

    /**
     * list
     * 
     * @param topLevelClass
     * @param introspectedTable
     * @return
     */
    private Method listEntitys(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("list");
        method.setReturnType(resultModelType);
        if (this.isRestful) {
            method.addAnnotation(getRestfulRequestMappingAnnotation(null, RequestMethod.GET));
        } else {
            method.addAnnotation("@RequestMapping(value = \"/list\", method = RequestMethod.GET)");
        }

        // swagger
        if (enableSwaggerAnnotation) {
            method.addAnnotation("@ApiOperation(value = \"列表\", notes = \"\", response = "
                                 + resultModelType.getShortName() + ".class)");
        }

        // 添加方法分页参数
        Parameter pageParameter = new Parameter(new FullyQualifiedJavaType("int"), "page");
        pageParameter.addAnnotation("@RequestParam(required = true)");
        method.addParameter(pageParameter);
        Parameter limitParameter = new Parameter(new FullyQualifiedJavaType("int"), "limit");
        limitParameter.addAnnotation("@RequestParam(required = true)");
        method.addParameter(limitParameter);

        // 调用Service
        method.addBodyLine(pagerType.getShortName() + "<" + modelType.getShortName() + "> pager = " + getService()
                           + "list(page, limit);");
        method.addBodyLine("return super.success(pager);");
        return method;
    }

    /**
     * 添加 Mapper依赖字段
     */
    private void addServiceField(TopLevelClass topLevelClass) {
        topLevelClass.addImportedType(serviceType);

        Field field = new Field();
        field.setVisibility(JavaVisibility.PRIVATE);
        field.setType(serviceType);
        field.setName(PluginUtils.lowerCaseFirstLetter(serviceType.getShortName()));
        field.addAnnotation("@Resource");
        topLevelClass.addField(field);
    }

    private String getService() {
        return PluginUtils.lowerCaseFirstLetter(serviceType.getShortName()) + ".";
    }

    private String getRestfulRequestMappingAnnotation(List<IntrospectedColumn> primaryKeyColumns,
                                                      RequestMethod method) {
        String methodStr = "";
        if (method == RequestMethod.POST) {
            methodStr = "RequestMethod.POST";
        } else if (method == RequestMethod.PUT) {
            methodStr = "RequestMethod.PUT";
        } else if (method == RequestMethod.PATCH) {
            methodStr = "RequestMethod.PATCH";
        } else if (method == RequestMethod.DELETE) {
            methodStr = "RequestMethod.DELETE";
        } else if (method == RequestMethod.GET) {
            methodStr = "RequestMethod.GET";
        }

        if (primaryKeyColumns == null) {
            return "@RequestMapping(method = " + methodStr + ")";
        } else {
            String pathStr = "";
            for (IntrospectedColumn primaryKeyColumn : primaryKeyColumns) {
                pathStr += "/{" + primaryKeyColumn.getJavaProperty() + "}";
            }
            return "@RequestMapping(value = \"" + pathStr + "\", method = " + methodStr + ")";
        }
    }

    private void addKeyParameters(Method method, List<IntrospectedColumn> primaryKeyColumns) {
        for (IntrospectedColumn primaryKeyColumn : primaryKeyColumns) {
            String javaProperty = primaryKeyColumn.getJavaProperty();
            Parameter parameter = new Parameter(primaryKeyColumn.getFullyQualifiedJavaType(), javaProperty);
            if (isRestful) {
                parameter.addAnnotation("@PathVariable(\"" + primaryKeyColumn.getJavaProperty() + "\")");
            } else {
                parameter.addAnnotation("@RequestParam(required = true)");
            }
            method.addParameter(parameter);
        }
    }

}
