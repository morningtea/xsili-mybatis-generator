/*
 * Copyright (c) 2016, XSILI and/or its affiliates. All rights reserved.
 * Use, Copy is subject to authorized license.
 */
package org.mybatis.generator.xsili;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.config.Context;
import org.mybatis.generator.xsili.plugins.util.PluginUtils;

/**
 * @author 叶鹏
 * @date 2017年12月15日
 */
public class GenHelper {
    
    /**
     * 是否包含逻辑删除列
     * 
     * @param introspectedTable
     * @return
     */
    public static boolean hasLogicDeletedField(IntrospectedTable introspectedTable) {
        return getLogicDeletedField(introspectedTable) != null;
    }

    /**
     * 获取逻辑删除列
     * 
     * @param introspectedTable
     * @return maybe null
     */
    public static IntrospectedColumn getLogicDeletedField(IntrospectedTable introspectedTable) {
        String logicDeletedName = PluginUtils.getPropertyNotNull(introspectedTable.getContext(), Constants.KEY_LOGIC_DELETED_NAME);
        for (IntrospectedColumn column : introspectedTable.getAllColumns()) {
            if (column.getJavaProperty().equals(logicDeletedName)) {
                return column;
            }
        }
        return null;
    }

    public static FullyQualifiedJavaType getBusinessExceptionType(Context context) {
        String businessExceptionName = context.getProperty(Constants.KEY_BUSINESS_EXCEPTION_QUALIFIED_NAME);
        if (StringUtils.isNotBlank(businessExceptionName)) {
            return new FullyQualifiedJavaType(businessExceptionName);
        } else {
            return new FullyQualifiedJavaType("java.lang.RuntimeException");
        }
    }
    
    

}
