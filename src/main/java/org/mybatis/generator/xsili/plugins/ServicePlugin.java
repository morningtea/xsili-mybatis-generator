package org.mybatis.generator.xsili.plugins;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.IntrospectedTable.TargetRuntime;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.Interface;
import org.mybatis.generator.api.dom.java.JavaVisibility;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.Parameter;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.xsili.GenHelper;
import org.mybatis.generator.xsili.plugins.testplugins.Jpa2RepositoryTestPlugin;
import org.mybatis.generator.xsili.plugins.util.PluginUtils;

/**
 * Service代码生成插件
 * 
 * @since 2.0.0
 * @author 叶鹏
 * @date 2017年8月25日
 */
public class ServicePlugin extends PluginAdapter {

    private String servicePackage;
    private String serviceImplPackage;
    private String project;
    private String modelPackage;

    private FullyQualifiedJavaType pageType;
    private FullyQualifiedJavaType idGeneratorType;

    private FullyQualifiedJavaType superInterfaceType;
    private FullyQualifiedJavaType superClassType;
    
    private FullyQualifiedJavaType serviceInterfaceType;
    private FullyQualifiedJavaType serviceType;
    private FullyQualifiedJavaType mapperType;
	// private FullyQualifiedJavaType baseModelType;
    /**
     * 如果有WithBlob, 则modelType赋值为BlobModel, 否则赋值为BaseModel
     */
    private FullyQualifiedJavaType allFieldModelType;
    private FullyQualifiedJavaType modelCriteriaType;
    private FullyQualifiedJavaType modelSubCriteriaType;
    private FullyQualifiedJavaType businessExceptionType;

    private FullyQualifiedJavaType listType = new FullyQualifiedJavaType("java.util.List");
    
    private FullyQualifiedJavaType slf4jLogger = new FullyQualifiedJavaType("org.slf4j.Logger");
    private FullyQualifiedJavaType slf4jLoggerFactory = new FullyQualifiedJavaType("org.slf4j.LoggerFactory");
    
    private FullyQualifiedJavaType annotationAutowired = new FullyQualifiedJavaType("org.springframework.beans.factory.annotation.Autowired");
    private FullyQualifiedJavaType annotationService = new FullyQualifiedJavaType("org.springframework.stereotype.Service");
    private FullyQualifiedJavaType annotationTransactional = new FullyQualifiedJavaType("org.springframework.transaction.annotation.Transactional");

    public ServicePlugin() {
        super();
    }

    @Override
    public boolean validate(List<String> warnings) {
        String page = properties.getProperty("page");
        if (StringUtils.isBlank(page)) {
            throw new RuntimeException("property page is null");
        } else {
            pageType = new FullyQualifiedJavaType(page);
        }

        String idGenerator = properties.getProperty("idGenerator");
        if (StringUtils.isNotBlank(idGenerator)) {
            idGeneratorType = new FullyQualifiedJavaType(idGenerator);
        }

        businessExceptionType = GenHelper.getBusinessExceptionType(context);

        servicePackage = properties.getProperty("targetPackage");
        serviceImplPackage = properties.getProperty("targetPackageImpl");
        project = properties.getProperty("targetProject");
        modelPackage = context.getJavaModelGeneratorConfiguration().getTargetPackage();

        return true;
    }

    @Override
    public List<GeneratedJavaFile> contextGenerateAdditionalJavaFiles(IntrospectedTable introspectedTable, List<TopLevelClass> modelClasses) {
        String table = introspectedTable.getBaseRecordType();
        String tableName = table.replaceAll(this.modelPackage + ".", "");

//        baseModelType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        allFieldModelType = introspectedTable.getRules().calculateAllFieldsClass();
        mapperType = new FullyQualifiedJavaType(introspectedTable.getMyBatis3JavaMapperType());
        serviceInterfaceType = new FullyQualifiedJavaType(servicePackage + "." + tableName + "Service");
        serviceType = new FullyQualifiedJavaType(serviceImplPackage + "." + tableName + "ServiceImpl");
        modelCriteriaType = new FullyQualifiedJavaType(introspectedTable.getExampleType());
        modelSubCriteriaType = new FullyQualifiedJavaType(introspectedTable.getExampleType() + ".Criteria");

        // 初始化Service父类/接口
        // 注意: 需要在allFieldModelType之后初始化
        String superInterface = properties.getProperty("superInterface");
        if(StringUtils.isNotBlank(superInterface)) {
            superInterfaceType = new FullyQualifiedJavaType(superInterface);
            superInterfaceType.addTypeArgument(allFieldModelType);
        }
        String superClass = properties.getProperty("superClass");
        if(StringUtils.isNotBlank(superClass)) {
            superClassType = new FullyQualifiedJavaType(superClass);
            superClassType.addTypeArgument(allFieldModelType);
        }
        
        List<GeneratedJavaFile> files = new ArrayList<GeneratedJavaFile>();
        Interface serviceInterface = new Interface(serviceInterfaceType);
        TopLevelClass serviceImplClass = new TopLevelClass(serviceType);
        
        // 导入必要的类
        addImport(serviceInterface, serviceImplClass, introspectedTable);
        // 接口 & 实现类
        generateFile(serviceInterface, serviceImplClass, introspectedTable, files);

        return files;
    }

    /**
     * 生成Service Interface & Impl
     * 
     * @param serviceInterface
     * @param serviceImplClass
     * @param introspectedTable
     * @param files
     */
    private void generateFile(Interface serviceInterface,
                                     TopLevelClass serviceImplClass,
                                     IntrospectedTable introspectedTable,
                                     List<GeneratedJavaFile> files) {
        // service接口
        serviceInterface.setVisibility(JavaVisibility.PUBLIC);
        // service实现类
        serviceImplClass.setVisibility(JavaVisibility.PUBLIC);
        
        // 设置Interface继承的接口
        if(superInterfaceType != null) {
            serviceInterface.addSuperInterface(superInterfaceType);
            // serviceInterface.addImportedType(superInterfaceType);
        }
        // 设置Impl继承的基类
        if(superClassType != null) {
            serviceImplClass.setSuperClass(superClassType);
            // serviceImplClass.addImportedType(superClassType);
        }
        // 设置Impl实现的接口
        serviceImplClass.addSuperInterface(serviceInterfaceType);
        
        // 添加注解
        serviceImplClass.addAnnotation("@Service");
        serviceImplClass.addImportedType(annotationService);
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            serviceImplClass.addAnnotation("@Transactional");
            serviceImplClass.addImportedType(annotationTransactional);
        }
        
        // 日志
        addLoggerField(serviceImplClass);
        
        // 添加构造方法
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            // 参数
            FullyQualifiedJavaType baseDaoType = GenHelper.getBaseDaoType(getContext());
            String baseDaoName = PluginUtils.getTypeParamName(baseDaoType);
            
            // 构造方法
            Method method = new Method();
            method.setConstructor(true);
            method.setVisibility(JavaVisibility.PUBLIC);
            method.setName(serviceImplClass.getType().getShortNameWithoutTypeArguments());
            method.addParameter(new Parameter(mapperType, baseDaoName));

            serviceImplClass.addMethod(method);
            
            method.addBodyLine("super("+baseDaoName+");");
        } else {
            // 添加 Mapper引用
            addMapperField(serviceImplClass);
        }
        

        // 方法
        // add
        Method addMethod = createEntity(serviceImplClass, introspectedTable);
        addMethod.removeBodyLines();
        serviceInterface.addMethod(addMethod);
        Method addMethodImpl = createEntity(serviceImplClass, introspectedTable);
        addMethodImpl.addAnnotation("@Override");
        serviceImplClass.addMethod(addMethodImpl);

        // update
        Method updateMethod = updateEntity(serviceImplClass, introspectedTable, false);
        updateMethod.removeBodyLines();
        serviceInterface.addMethod(updateMethod);
        Method updateMethodImpl = updateEntity(serviceImplClass, introspectedTable, false);
        updateMethodImpl.addAnnotation("@Override");
        serviceImplClass.addMethod(updateMethodImpl);
        // updateSelective
        if (introspectedTable.getTargetRuntime() != TargetRuntime.JPA2) {
            Method updateSelectiveMethod = updateEntity(serviceImplClass, introspectedTable, true);
            updateSelectiveMethod.removeBodyLines();
            serviceInterface.addMethod(updateSelectiveMethod);
            Method updateSelectiveMethodImpl = updateEntity(serviceImplClass, introspectedTable, true);
            updateSelectiveMethodImpl.addAnnotation("@Override");
            serviceImplClass.addMethod(updateSelectiveMethodImpl);
        }

        if(introspectedTable.getTargetRuntime() == TargetRuntime.MYBATIS3) {
            // updateDirect
            Method updateDirectMethodImpl = updateDirectMybatis(serviceImplClass, introspectedTable, false);
            serviceImplClass.addMethod(updateDirectMethodImpl);
            // updateSelectiveDirect
            Method updateSelectiveDirectMethodImpl = updateDirectMybatis(serviceImplClass, introspectedTable, true);
            serviceImplClass.addMethod(updateSelectiveDirectMethodImpl);
        }
        
        // delete
        // jpa2, deleteById方法放到了基类(#id1)
        if(hasMultiKeys(introspectedTable) || introspectedTable.getTargetRuntime() != TargetRuntime.JPA2) {
            IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
            if(logicDeletedColumn == null) {
                // deletePhysically
                Method deletePhysicallyMethod = deletePhysicallyEntity(introspectedTable);
                deletePhysicallyMethod.removeBodyLines();
                serviceInterface.addMethod(deletePhysicallyMethod);
                Method deletePhysicallyMethodImpl = deletePhysicallyEntity(introspectedTable);
                deletePhysicallyMethodImpl.addAnnotation("@Override");
                serviceImplClass.addMethod(deletePhysicallyMethodImpl);
            } else {
                // deleteLogically
                Method deleteLogicallyMethod = deleteLogicallyEntity(introspectedTable, serviceImplClass);
                if (deleteLogicallyMethod != null) {
                    deleteLogicallyMethod.removeBodyLines();
                    serviceInterface.addMethod(deleteLogicallyMethod);
                }
                Method deleteLogicallyMethodImpl = deleteLogicallyEntity(introspectedTable, serviceImplClass);
                if (deleteLogicallyMethodImpl != null) {
                    deleteLogicallyMethodImpl.addAnnotation("@Override");
                    serviceImplClass.addMethod(deleteLogicallyMethodImpl);
                }
            }
        }

        // get
        // jpa2, getById方法放到了基类(#id1)
        if(hasMultiKeys(introspectedTable) || introspectedTable.getTargetRuntime() != TargetRuntime.JPA2) {
            Method getMethod = getEntity(introspectedTable);
            getMethod.removeBodyLines();
            serviceInterface.addMethod(getMethod);
            Method getMethodImpl = getEntity(introspectedTable);
            getMethodImpl.addAnnotation("@Override");
            serviceImplClass.addMethod(getMethodImpl);
        }

        // list
        Method listMethod = listEntity(introspectedTable, serviceImplClass);
        listMethod.removeBodyLines();
        serviceInterface.addMethod(listMethod);
        Method listMethodImpl = listEntity(introspectedTable, serviceImplClass);
        listMethodImpl.addAnnotation("@Override");
        serviceImplClass.addMethod(listMethodImpl);

        // 生成文件
        GeneratedJavaFile interfaceFile = new GeneratedJavaFile(serviceInterface, project, context.getProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING), context.getJavaFormatter());
        files.add(interfaceFile);
        GeneratedJavaFile implFile = new GeneratedJavaFile(serviceImplClass, project, context.getProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING), context.getJavaFormatter());
        files.add(implFile);
    }
    

    /**
     * 导入需要的类
     */
    private void addImport(Interface serviceInterface,
                           TopLevelClass serviceImplClass,
                           IntrospectedTable introspectedTable) {
        // 导入key类型
        List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
        for (Parameter keyParameter : keyParameterList) {
            if (keyParameter.getName().equals(PluginUtils.PRIMARY_KEY_PARAMETER_NAME)) {
                serviceInterface.addImportedType(keyParameter.getType());
                serviceImplClass.addImportedType(keyParameter.getType());
            }
        }

        // 接口
        // serviceInterface.addImportedType(baseModelType);
        serviceInterface.addImportedType(allFieldModelType);
        serviceInterface.addImportedType(pageType);

        // 实现类
        serviceImplClass.addImportedType(serviceInterfaceType);
        serviceImplClass.addImportedType(slf4jLogger);
        serviceImplClass.addImportedType(slf4jLoggerFactory);

        serviceImplClass.addImportedType(mapperType);
        // serviceImplClass.addImportedType(baseModelType);
        serviceImplClass.addImportedType(allFieldModelType);

        serviceImplClass.addImportedType(annotationService);
        serviceImplClass.addImportedType(annotationAutowired);
    }

    
    /**
     * add
     * 
     * @param serviceImplClass
     * @param introspectedTable
     * @return
     */
    private Method createEntity(TopLevelClass serviceImplClass, IntrospectedTable introspectedTable) {
        String modelParamName = PluginUtils.getTypeParamName(allFieldModelType);

        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("create");
        method.setReturnType(allFieldModelType);
        method.addParameter(new Parameter(allFieldModelType, modelParamName));

        String createdTimeField = GenHelper.getCreatedTimeField(introspectedTable);
        String updatedTimeField = GenHelper.getUpdatedTimeField(introspectedTable);
        IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
        
        List<IntrospectedColumn> introspectedColumns = introspectedTable.getAllColumns();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            if (PluginUtils.isPrimaryKey(introspectedTable, introspectedColumn)) {
                // if (!introspectedColumn.isIdentity() && !introspectedColumn.isAutoIncrement()) {
                // }
                // 字符类型主键
                if (introspectedColumn.isStringColumn() && idGeneratorType != null) {
                    serviceImplClass.addImportedType(idGeneratorType);
                    String params = idGeneratorType.getShortName() + ".generateId()";
                    method.addBodyLine(PluginUtils.generateSetterCall(modelParamName, introspectedColumn.getJavaProperty(), params, true));
                }
            } else if (createdTimeField.equals(introspectedColumn.getJavaProperty())) {
                serviceImplClass.addImportedType(new FullyQualifiedJavaType("java.util.Date"));
                method.addBodyLine(modelParamName + ".set" + PluginUtils.upperCaseFirstLetter(createdTimeField) + "(new Date());");
            } else if (updatedTimeField.equals(introspectedColumn.getJavaProperty())) {
                serviceImplClass.addImportedType(new FullyQualifiedJavaType("java.util.Date"));
                method.addBodyLine(modelParamName + ".set" + PluginUtils.upperCaseFirstLetter(updatedTimeField) + "(new Date());");
            } else if (logicDeletedColumn != null && logicDeletedColumn.getJavaProperty().equals(introspectedColumn.getJavaProperty())) {
                method.addBodyLine(modelParamName + ".set" + PluginUtils.upperCaseFirstLetter(logicDeletedColumn.getJavaProperty()) + "(false);");
            }
        }
        
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            method.addBodyLine("return " + getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "create") + "(" + modelParamName + ");");
        } else {
            serviceImplClass.addImportedType(businessExceptionType);
            
            method.addBodyLine("if (" + getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "create") + "(" + modelParamName + ") == 0) {");
            method.addBodyLine("throw new " + businessExceptionType.getShortName() + "(\"插入数据库失败\");");
            method.addBodyLine("}");
            method.addBodyLine("return " + modelParamName + ";");
        }
        
        return method;
    }
    
    private Method updateEntity(TopLevelClass serviceImplClass, IntrospectedTable introspectedTable, boolean isSelective) {
    	String modelParamName = PluginUtils.getTypeParamName(allFieldModelType);

        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        if(isSelective) {
            method.setName("updateSelective");
        } else {
            method.setName("update");
        }
        method.setReturnType(allFieldModelType);
        method.addParameter(new Parameter(allFieldModelType, modelParamName));

        String serviceMethodName = "";
        if (introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            if (isSelective) {
                serviceMethodName = "super.updateNotNull";
            } else {
                serviceMethodName = "super.updateAll";
            }
        }
        if (introspectedTable.getTargetRuntime() == TargetRuntime.MYBATIS3) {
            if (isSelective) {
                serviceMethodName = "this.updateSelectiveDirect";
            } else {
                serviceMethodName = "this.updateDirect";
            }
        }

        method.addBodyLine("return " + serviceMethodName + "(" + modelParamName + ");");
        return method;
    }
    
    /**
     * updateDirect
     * 
     * @param serviceImplClass
     * @param introspectedTable
     * @return
     */
    private Method updateDirectMybatis(TopLevelClass serviceImplClass, IntrospectedTable introspectedTable, boolean isSelective) {
        if(introspectedTable.getTargetRuntime() != TargetRuntime.MYBATIS3) {
            throw new RuntimeException("updateDirectMybatis only support Mybatis");
        }
        
        String modelParamName = PluginUtils.getTypeParamName(allFieldModelType);

        Method method = new Method();
        method.setVisibility(JavaVisibility.PRIVATE);
        if(isSelective) {
            method.setName("updateSelectiveDirect");
        } else {
            method.setName("updateDirect");
        }
        method.setReturnType(allFieldModelType);
        method.addParameter(new Parameter(allFieldModelType, modelParamName));

        String updatedTimeField = GenHelper.getUpdatedTimeField(introspectedTable);
        List<IntrospectedColumn> introspectedColumns = introspectedTable.getAllColumns();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            // mysql date introspectedColumn.isJDBCDateColumn()
            // mysql time introspectedColumn.isJDBCTimeColumn()
            // mysql dateTime ??

            if (updatedTimeField.equals(introspectedColumn.getJavaProperty())) {
                serviceImplClass.addImportedType(new FullyQualifiedJavaType("java.util.Date"));
                method.addBodyLine(modelParamName + ".set" + PluginUtils.upperCaseFirstLetter(updatedTimeField) + "(new Date());");
            }
        }
        
        String mapperMethodName = "";
        if(isSelective) {
            mapperMethodName = getMapperMethodName(introspectedTable, "updateSelective");
        } else {
            mapperMethodName = getMapperMethodName(introspectedTable, "update");
        }
        
        serviceImplClass.addImportedType(businessExceptionType);
        method.addBodyLine("if (" + getMapper(introspectedTable) + mapperMethodName + "(" + modelParamName + ") == 0) {");
        method.addBodyLine("throw new " + businessExceptionType.getShortName() + "(\"记录不存在\");");
        method.addBodyLine("}");
        method.addBodyLine("return " + modelParamName + ";");

        return method;
    }
    
    
    /**
     * 如果包含逻辑删除列, 则返回逻辑删除的方法, 否则返回null
     * 
     * @param introspectedTable
     * @return maybe null
     */
    private Method deleteLogicallyEntity(IntrospectedTable introspectedTable, TopLevelClass serviceImplClass) {
        IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
        if(logicDeletedColumn == null) {
            return null;
        }
        
        Method method = new Method();
        method.setName("deleteById");
        method.setVisibility(JavaVisibility.PUBLIC);
        method.addJavaDocLine("/** 逻辑删除 */");
        
        List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
        for (Parameter keyParameter : keyParameterList) {
            method.addParameter(keyParameter);
        }
        String params = PluginUtils.getCallParameters(keyParameterList);
        
        // 填充key
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            String keyParams = prepareCallByEntityAsKey(introspectedTable, method, method.getParameters());
            if(StringUtils.isNotBlank(keyParams)) {
                params = keyParams;
            }
        }
        
        String modelParamName = "exist";
        if (introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            // 导入依赖类
            serviceImplClass.addImportedType(new FullyQualifiedJavaType("java.util.Optional"));
            method.addBodyLine("Optional<" + allFieldModelType.getShortName() + "> " + modelParamName + " = "
                               + getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "get") + "(" + params + ");");
            method.addBodyLine(modelParamName + ".ifPresent((v) -> {");
            method.addBodyLine("v.set" + PluginUtils.upperCaseFirstLetter(logicDeletedColumn.getJavaProperty()) + "(true);");
            method.addBodyLine("this.updateDirect(v);");
            method.addBodyLine("});");
        } else {
            method.addBodyLine(allFieldModelType.getShortName() + " " + modelParamName + " = " + getMapper(introspectedTable)
                               + getMapperMethodName(introspectedTable, "get") + "(" + params + ");");
            method.addBodyLine("if (" + modelParamName + " != null) {");
            method.addBodyLine(modelParamName + ".set" + PluginUtils.upperCaseFirstLetter(logicDeletedColumn.getJavaProperty()) + "(true);");
            method.addBodyLine("this.update(" + modelParamName + ");");
            method.addBodyLine("}");
        }
        
        return method;
    }
    
    

    /**
     * deletePhysically
     * 
     * @param introspectedTable
     * @return
     */
    private Method deletePhysicallyEntity(IntrospectedTable introspectedTable) {
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setName("deleteById");
        method.addJavaDocLine("/** 物理删除 */");

        List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
        for (Parameter keyParameter : keyParameterList) {
            method.addParameter(keyParameter);
        }
        String params = PluginUtils.getCallParameters(keyParameterList);
        
        // 填充key
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            String keyParams = prepareCallByEntityAsKey(introspectedTable, method, method.getParameters());
            if(StringUtils.isNotBlank(keyParams)) {
                params = keyParams;
            }
        }
        method.addBodyLine(getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "delete") + "(" + params + ");");
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
        method.setName("getById");
        method.setReturnType(allFieldModelType);
        // 方法参数
        List<Parameter> keyParameterList = PluginUtils.getPrimaryKeyParameters(introspectedTable);
        for (Parameter keyParameter : keyParameterList) {
            method.addParameter(keyParameter);
        }

        String modelObj = PluginUtils.lowerCaseFirstLetter(allFieldModelType.getShortName());

        // 方法参数为空, 则直接返回
        String conditionNotNull = "";
        for (Parameter keyParameter : keyParameterList) {
            conditionNotNull = conditionNotNull + " || " + keyParameter.getName() + " == null";
        }
        conditionNotNull = conditionNotNull.substring(4);
        method.addBodyLine("if (" + conditionNotNull + ") {");
        method.addBodyLine("return null;");
        method.addBodyLine("}");
        method.addBodyLine("");

        // 查询数据
        String params = PluginUtils.getCallParameters(keyParameterList);
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            String keyParams = prepareCallByEntityAsKey(introspectedTable, method, method.getParameters());
            if(StringUtils.isNotBlank(keyParams)) {
                params = keyParams;
            }
            method.addBodyLine(allFieldModelType.getShortName() + " " + modelObj + " = " + getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "get") + "(" + params + ").orElse(null);");
        } else {
            method.addBodyLine(allFieldModelType.getShortName() + " " + modelObj + " = " + getMapper(introspectedTable) + getMapperMethodName(introspectedTable, "get") + "(" + params + ");");
        }
        
        // 判断是否已被逻辑删除
        IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
        if(logicDeletedColumn != null) {
            method.addBodyLine("if (" + modelObj + " == null || " + modelObj + ".getIsDeleted()) {");
            method.addBodyLine("return null;");
            method.addBodyLine("}");
        }
        method.addBodyLine("return " + modelObj + ";");
        
        return method;
    }
    

    /**
     * list
     * 
     * @param serviceImplClass
     * @param introspectedTable
     * @return
     */
    private Method listEntity(IntrospectedTable introspectedTable, TopLevelClass serviceImplClass) {
        if(introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            return listEntityJpa2(introspectedTable, serviceImplClass);
        } else {
            return listEntityMybatis(introspectedTable, serviceImplClass);
        }
    }
    
    
    /**
     * list mybatis
     * 
     * @param serviceImplClass
     * @param introspectedTable
     * @return
     */
    private Method listEntityMybatis(IntrospectedTable introspectedTable, TopLevelClass serviceImplClass) {
        Method method = new Method();
        method.setName("list");
        method.setReturnType(new FullyQualifiedJavaType(pageType.getShortName() + "<" + allFieldModelType.getShortName()
                                                        + ">"));
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "pageNum"));
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "pageSize"));
        method.setVisibility(JavaVisibility.PUBLIC);

        // 导入类
        serviceImplClass.addImportedType(modelCriteriaType);
        serviceImplClass.addImportedType(modelSubCriteriaType);
        serviceImplClass.addImportedType(listType);
        serviceImplClass.addImportedType(pageType);
        
        method.addBodyLine(modelCriteriaType.getShortName() + " criteria = new " + modelCriteriaType.getShortName()
                           + "();");
        method.addBodyLine("criteria.setPage(pageNum);");
        method.addBodyLine("criteria.setLimit(pageSize);");
        
        IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
        if(logicDeletedColumn == null) {// 逻辑删除
            method.addBodyLine("@SuppressWarnings(\"unused\")");
            method.addBodyLine(modelSubCriteriaType.getShortName() + " cri = criteria.createCriteria();");
        } else {
            method.addBodyLine(modelSubCriteriaType.getShortName() + " cri = criteria.createCriteria();");
            method.addBodyLine("cri.and" + PluginUtils.upperCaseFirstLetter(logicDeletedColumn.getJavaProperty()) + "EqualTo(false);");
        }

        method.addBodyLine("List<" + allFieldModelType.getShortName() + "> list = " + getMapper(introspectedTable)
                           + "selectByExample(criteria);");
        method.addBodyLine("return " + pageType.getShortName() + ".buildPage(list, criteria);");
        return method;
    }
    

    /**
     * list jpa2
     * 
     * @param serviceImplClass
     * @param introspectedTable
     * @return
     */
    private Method listEntityJpa2(IntrospectedTable introspectedTable, TopLevelClass serviceImplClass) {
        Method method = new Method();
        method.setName("list");
        method.setReturnType(new FullyQualifiedJavaType(pageType.getShortName() + "<" + allFieldModelType.getShortName()
                                                        + ">"));
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "pageNum"));
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "pageSize"));
        method.setVisibility(JavaVisibility.PUBLIC);

        // 导入类
        serviceImplClass.addImportedType(pageType);
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("com.querydsl.core.types.Predicate"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("com.querydsl.core.types.ExpressionUtils"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.Page"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.PageRequest"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.Pageable"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.Sort"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.Sort.Direction"));
        serviceImplClass.addImportedType(new FullyQualifiedJavaType("org.springframework.data.domain.Sort.Order"));
        
        String qModelType = "Q"+allFieldModelType.getShortName();
        serviceImplClass.addImportedType(new FullyQualifiedJavaType(allFieldModelType.getPackageName() + "." + qModelType));

        String qModelTypeObj = "q"+allFieldModelType.getShortName();
        
        method.addBodyLine(qModelType + " " + qModelTypeObj + " = " + qModelType + "." + PluginUtils.lowerCaseFirstLetter(allFieldModelType.getShortName()) + ";");
        method.addBodyLine("Predicate predicate = null;");
        // method.addBodyLine("// 示例, 可以去掉");
        method.addBodyLine("predicate = ExpressionUtils.and(predicate, " + qModelTypeObj + ".id.isNotNull());");
        // 逻辑删除
        IntrospectedColumn logicDeletedColumn = GenHelper.getLogicDeletedColumn(introspectedTable);
        if(logicDeletedColumn != null) {
            method.addBodyLine("predicate = ExpressionUtils.and(predicate, " + qModelTypeObj + "." + logicDeletedColumn.getJavaProperty() + ".eq(false));");
        }
        
        method.addBodyLine("");
        method.addBodyLine("Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(new Order(Direction.DESC, \"id\")));");
        method.addBodyLine("Page<" + allFieldModelType.getShortName() + "> page = " + getMapper(introspectedTable) + "findAll(predicate, pageable);");
        method.addBodyLine("return " + pageType.getShortName() + ".buildPage(page);");
        
        return method;
    }
    

    /**
     * 添加 LOGGER 字段
     */
    private void addLoggerField(TopLevelClass serviceImplClass) {
        Field field = new Field();
        field.addAnnotation("@SuppressWarnings(\"unused\")");
        field.setVisibility(JavaVisibility.PRIVATE);
        field.setStatic(true);
        field.setFinal(true);
        field.setType(new FullyQualifiedJavaType("Logger"));
        field.setName("LOGGER");
        // 设置值
        field.setInitializationString("LoggerFactory.getLogger(" + serviceImplClass.getType().getShortName() + ".class)");
        serviceImplClass.addField(field);
    }
    
    /**
     * 添加 Mapper依赖字段
     */
    private void addMapperField(TopLevelClass serviceImplClass) {
        serviceImplClass.addImportedType(mapperType);

        Field field = new Field();
        field.setVisibility(JavaVisibility.PRIVATE);
        field.setType(mapperType);
        field.setName(PluginUtils.lowerCaseFirstLetter(mapperType.getShortNameWithoutTypeArguments()));
        field.addAnnotation("@" + annotationAutowired.getShortName());
        serviceImplClass.addField(field);
    }

    private String getMapper(IntrospectedTable introspectedTable) {
        if (introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
            FullyQualifiedJavaType baseDaoType = GenHelper.getBaseDaoType(getContext());
            return "super." + PluginUtils.getTypeParamName(baseDaoType) + ".";
        } else {
            return "this." + PluginUtils.lowerCaseFirstLetter(mapperType.getShortNameWithoutTypeArguments()) + ".";
        }
    }
    
    
    /**
     * 
     * @param introspectedTable
     * @param type create update updateSelective delete get
     * @return
     */
    private String getMapperMethodName(IntrospectedTable introspectedTable, String type) {
        if (type.equals("create")) {
            return introspectedTable.getTargetRuntime() == TargetRuntime.JPA2 ? "save" : "insertSelective";
        } else if (type.equals("update")) {
            if (introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
                return "saveAndFlush";
            } else {
                if (PluginUtils.hasBLOBColumns(introspectedTable)) {
                    return "updateByPrimaryKeyWithBLOBs";
                } else {
                    return "updateByPrimaryKey";
                }
            }
        } else if (type.equals("updateSelective")) {
            if (introspectedTable.getTargetRuntime() == TargetRuntime.JPA2) {
                // TODO jpa2 updateSelective
                return "updateNotNull";
            } else {
                return "updateByPrimaryKeySelective";
            }
        } else if (type.equals("delete")) {
            return introspectedTable.getTargetRuntime() == TargetRuntime.JPA2 ? "deleteById" : "deleteByPrimaryKey";
        } else if (type.equals("get")) {
            return introspectedTable.getTargetRuntime() == TargetRuntime.JPA2 ? "findById" : "selectByPrimaryKey";
        } else {
            throw new IllegalArgumentException("参数type错误, type: " + type);
        }
    }

    private boolean hasMultiKeys(IntrospectedTable introspectedTable) {
        List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
        return primaryKeyColumns.size() > 1;
    }

    /**
     * 如果没有生成主键类, 并且是复合主键, 则以allFieldModel填充复合主键, 并返回调用参数<br>
     * {@link Jpa2RepositoryTestPlugin#prepareCallByEntityAsKey}
     * @param introspectedTable
     * @param caller
     * @return
     */
    private String prepareCallByEntityAsKey(IntrospectedTable introspectedTable, Method caller, List<Parameter> keyParameters) {
        // 检查是否包含主键
        PluginUtils.checkPrimaryKey(introspectedTable);
        
        if (!introspectedTable.getRules().generatePrimaryKeyClass()) {
            List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
            if (primaryKeyColumns.size() > 1) {
                // String modelParamName = PluginUtils.getTypeParamName(allFieldModelType);
                String modelParamName = "new" + allFieldModelType.getShortName();
                // 填充key参数
                caller.addBodyLine(allFieldModelType.getShortName() + " " + modelParamName + " = new "
                                   + allFieldModelType.getShortName() + "();");
                PluginUtils.generateModelSetterBodyLine(modelParamName, caller, keyParameters);
                // call param
                return modelParamName;
            }
        }
        return null;
    }
    
}
