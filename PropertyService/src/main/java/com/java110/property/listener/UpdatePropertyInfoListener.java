package com.java110.property.listener;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.java110.property.dao.IPropertyServiceDao;
import com.java110.common.constant.ResponseConstant;
import com.java110.common.constant.ServiceCodeConstant;
import com.java110.common.constant.StatusConstant;
import com.java110.common.exception.ListenerExecuteException;
import com.java110.common.util.Assert;
import com.java110.core.annotation.Java110Listener;
import com.java110.core.context.DataFlowContext;
import com.java110.entity.center.Business;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 修改物业信息 侦听
 *
 * 处理节点
 * 1、businessPropertyInfo:{} 物业基本信息节点
 * 2、businessPropertyAttr:[{}] 物业属性信息节点
 * Created by wuxw on 2018/5/18.
 */
@Java110Listener("updatePropertyInfoListener")
@Transactional
public class UpdatePropertyInfoListener extends AbstractPropertyBusinessServiceDataFlowListener {

    private final static Logger logger = LoggerFactory.getLogger(UpdatePropertyInfoListener.class);
    @Autowired
    IPropertyServiceDao propertyServiceDaoImpl;

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public String getServiceCode() {
        return ServiceCodeConstant.SERVICE_CODE_UPDATE_PROPERTY_INFO;
    }

    /**
     * business过程
     * @param dataFlowContext
     * @param business
     */
    @Override
    protected void doSaveBusiness(DataFlowContext dataFlowContext, Business business) {

        JSONObject data = business.getDatas();

        Assert.notEmpty(data,"没有datas 节点，或没有子节点需要处理");

        //处理 businessProperty 节点
        if(data.containsKey("businessProperty")){
            JSONObject businessProperty = data.getJSONObject("businessProperty");
            doBusinessProperty(business,businessProperty);
            dataFlowContext.addParamOut("propertyId",businessProperty.getString("propertyId"));
        }

        if(data.containsKey("businessPropertyAttr")){
            JSONArray businessPropertyAttrs = data.getJSONArray("businessPropertyAttr");
            doSaveBusinessPropertyAttrs(business,businessPropertyAttrs);
        }
    }


    /**
     * business to instance 过程
     * @param dataFlowContext 数据对象
     * @param business 当前业务对象
     */
    @Override
    protected void doBusinessToInstance(DataFlowContext dataFlowContext, Business business) {

        JSONObject data = business.getDatas();

        Map info = new HashMap();
        info.put("bId",business.getbId());
        info.put("operate",StatusConstant.OPERATE_ADD);

        //物业信息
        Map businessPropertyInfo = propertyServiceDaoImpl.getBusinessPropertyInfo(info);
        if( businessPropertyInfo != null && !businessPropertyInfo.isEmpty()) {
            flushBusinessPropertyInfo(businessPropertyInfo,StatusConstant.STATUS_CD_VALID);
            propertyServiceDaoImpl.updatePropertyInfoInstance(businessPropertyInfo);
            dataFlowContext.addParamOut("propertyId",businessPropertyInfo.get("property_id"));
        }
        //物业属性
        List<Map> businessPropertyAttrs = propertyServiceDaoImpl.getBusinessPropertyAttrs(info);
        if(businessPropertyAttrs != null && businessPropertyAttrs.size() > 0) {
            for(Map businessPropertyAttr : businessPropertyAttrs) {
                flushBusinessPropertyAttr(businessPropertyAttr,StatusConstant.STATUS_CD_VALID);
                propertyServiceDaoImpl.updatePropertyAttrInstance(businessPropertyAttr);
            }
        }
        //物业照片
        List<Map> businessPropertyPhotos = propertyServiceDaoImpl.getBusinessPropertyPhoto(info);
        if(businessPropertyPhotos != null && businessPropertyPhotos.size() >0){
            for(Map businessPropertyPhoto : businessPropertyPhotos) {
                flushBusinessPropertyPhoto(businessPropertyPhoto,StatusConstant.STATUS_CD_VALID);
                propertyServiceDaoImpl.updatePropertyPhotoInstance(businessPropertyPhoto);
            }
        }
        //物业证件
        List<Map> businessPropertyCerdentialses = propertyServiceDaoImpl.getBusinessPropertyCerdentials(info);
        if(businessPropertyCerdentialses != null && businessPropertyCerdentialses.size()>0){
            for(Map businessPropertyCerdentials : businessPropertyCerdentialses) {
                flushBusinessPropertyCredentials(businessPropertyCerdentials,StatusConstant.STATUS_CD_VALID);
                propertyServiceDaoImpl.updatePropertyCerdentailsInstance(businessPropertyCerdentials);
            }
        }
    }

    /**
     * 撤单
     * @param dataFlowContext 数据对象
     * @param business 当前业务对象
     */
    @Override
    protected void doRecover(DataFlowContext dataFlowContext, Business business) {

        String bId = business.getbId();
        //Assert.hasLength(bId,"请求报文中没有包含 bId");
        Map info = new HashMap();
        info.put("bId",bId);
        info.put("statusCd",StatusConstant.STATUS_CD_VALID);
        Map delInfo = new HashMap();
        delInfo.put("bId",business.getbId());
        delInfo.put("operate",StatusConstant.OPERATE_DEL);
        //物业信息
        Map propertyInfo = propertyServiceDaoImpl.getPropertyInfo(info);
        if(propertyInfo != null && !propertyInfo.isEmpty()){

            //物业信息
            Map businessPropertyInfo = propertyServiceDaoImpl.getBusinessPropertyInfo(delInfo);
            //除非程序出错了，这里不会为空
            if(businessPropertyInfo == null || businessPropertyInfo.isEmpty()){
                throw new ListenerExecuteException(ResponseConstant.RESULT_CODE_INNER_ERROR,"撤单失败（property），程序内部异常,请检查！ "+delInfo);
            }

            flushBusinessPropertyInfo(businessPropertyInfo,StatusConstant.STATUS_CD_VALID);
            propertyServiceDaoImpl.updatePropertyInfoInstance(businessPropertyInfo);
            dataFlowContext.addParamOut("propertyId",propertyInfo.get("property_id"));
        }

        //物业属性
        List<Map> propertyAttrs = propertyServiceDaoImpl.getPropertyAttrs(info);
        if(propertyAttrs != null && propertyAttrs.size()>0){

            List<Map> businessPropertyAttrs = propertyServiceDaoImpl.getBusinessPropertyAttrs(delInfo);
            //除非程序出错了，这里不会为空
            if(businessPropertyAttrs == null || businessPropertyAttrs.size() ==0 ){
                throw new ListenerExecuteException(ResponseConstant.RESULT_CODE_INNER_ERROR,"撤单失败(property_attr)，程序内部异常,请检查！ "+delInfo);
            }
            for(Map businessPropertyAttr : businessPropertyAttrs) {
                flushBusinessPropertyAttr(businessPropertyAttr,StatusConstant.STATUS_CD_VALID);
                propertyServiceDaoImpl.updatePropertyAttrInstance(businessPropertyAttr);
            }
        }
    }

    /**
     * 处理 businessProperty 节点
     * @param business 总的数据节点
     * @param businessProperty 物业节点
     */
    private void doBusinessProperty(Business business,JSONObject businessProperty){

        Assert.jsonObjectHaveKey(businessProperty,"propertyId","businessProperty 节点下没有包含 propertyId 节点");

        if(businessProperty.getString("propertyId").startsWith("-")){
            throw new ListenerExecuteException(ResponseConstant.RESULT_PARAM_ERROR,"propertyId 错误，不能自动生成（必须已经存在的propertyId）"+businessProperty);
        }
        //自动保存DEL
        autoSaveDelBusinessProperty(business,businessProperty);

        businessProperty.put("bId",business.getbId());
        businessProperty.put("operate", StatusConstant.OPERATE_ADD);
        //保存物业信息
        propertyServiceDaoImpl.saveBusinessPropertyInfo(businessProperty);

    }



    /**
     * 保存物业属性信息
     * @param business 当前业务
     * @param businessPropertyAttrs 物业属性
     */
    private void doSaveBusinessPropertyAttrs(Business business,JSONArray businessPropertyAttrs){
        JSONObject data = business.getDatas();


        for(int propertyAttrIndex = 0 ; propertyAttrIndex < businessPropertyAttrs.size();propertyAttrIndex ++){
            JSONObject propertyAttr = businessPropertyAttrs.getJSONObject(propertyAttrIndex);
            Assert.jsonObjectHaveKey(propertyAttr,"attrId","businessPropertyAttr 节点下没有包含 attrId 节点");
            if(propertyAttr.getString("attrId").startsWith("-")){
                throw new ListenerExecuteException(ResponseConstant.RESULT_PARAM_ERROR,"attrId 错误，不能自动生成（必须已经存在的attrId）"+propertyAttr);
            }
            //自动保存DEL数据
            autoSaveDelBusinessPropertyAttr(business,propertyAttr);

            propertyAttr.put("bId",business.getbId());
            propertyAttr.put("propertyId",propertyAttr.getString("propertyId"));
            propertyAttr.put("operate", StatusConstant.OPERATE_ADD);

            propertyServiceDaoImpl.saveBusinessPropertyAttr(propertyAttr);
        }
    }


    public IPropertyServiceDao getPropertyServiceDaoImpl() {
        return propertyServiceDaoImpl;
    }

    public void setPropertyServiceDaoImpl(IPropertyServiceDao propertyServiceDaoImpl) {
        this.propertyServiceDaoImpl = propertyServiceDaoImpl;
    }



}
