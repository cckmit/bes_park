package com.efounder.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.core.common.ISSPReturnObject;
import com.efounder.JEnterprise.common.util.UUIDUtil;
import com.efounder.JEnterprise.domain.SysJob;
import com.efounder.JEnterprise.initializer.SbPzStructCache;
import com.efounder.JEnterprise.mapper.quartz.SysJobPlanMapper;
import com.efounder.JEnterprise.model.basedatamanage.eqmanage.BESSbPzStruct;
import com.efounder.JEnterprise.service.basedatamanage.eqmanage.BESSbdyService;
import com.efounder.JEnterprise.service.basedatamanage.eqmanage.EnerEquipmentService;
import com.efounder.util.StringUtils;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.rpc.ServiceException;
import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 定时任务调度测试
 *@Data 2021/1/20 10:18
 * @author sunzhiyuan
 */
@Component("besTask")
public class BesExecute {

    @Autowired
    private SysJobPlanMapper sysJobPlanMapper;

    //设备树缓存
    @Autowired
    private SbPzStructCache sbPzStructCache;

    @Autowired
    private BESSbdyService besSbdyService;

    @Autowired
    private EnerEquipmentService enerEquipmentService;

    public void besMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        System.out.println(StringUtils.format("执行多参方法： 字符串类型{}，布尔类型{}，长整型{}，浮点型{}，整形{}", s, b, l, d, i));
    }

    public void besParams(String params)
    {
        System.out.println("执行有参方法：" + params);
    }

    public void sysNoParams()
    {
        System.out.println("执行无参方法");
    }

    //执行时间类型的定时任务
    public void executePlanTaskInfo(SysJob sysJob){

        System.out.println("计划任务走了***********************************:sysJob.name"+sysJob.getJobName());
        List<Map<String,Object>> infoList = new ArrayList<>();

        String planId = sysJob.getPlanId();

        Map<String,Object> planMap = sysJobPlanMapper.queryPlanModeIdAndSceneId(planId);
        if (planMap == null) {
            return;
        }

        if ("0".equals(planMap.get("f_invoke"))) {//是否执行(1执行 0不执行)
            return;
        }

        Map<String,Object> taskInfo = sysJobPlanMapper.queryTimeTaskList(planId);

        if (null == taskInfo){
            return;
        }else {

            String taskId = (String) taskInfo.get("f_id");

            String timename = (String) taskInfo.get("f_timename");

            planMap.put("taskId",taskId);

            planMap.put("timename",timename);

            infoList.add(planMap);

            executeTimeTaskInfo(infoList);
        }
    }

    public void executeTimeTaskInfo(List<Map<String, Object>> infoList){

        for (int i =0;i<infoList.size();i++){

            Map<String,Object> map = infoList.get(i);

            String modeId = (String) map.get("f_modeInfoId");

            List<Map<String,Object>> pointList = sysJobPlanMapper.selectPointInfomationByModeId(modeId);

            if (pointList == null) {
                return;
            }

            JudgePointType(pointList,map);
        }
    }

    //因为会出现并发情况 存储时间数据 所以不能单个的存储数据
    public void JudgePointType(List<Map<String, Object>> pointList,Map map){
        Map<String,Object> typeDiPointMap = new HashMap<>();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String F_CRDATE = simpleDateFormat.format(new Date());

        for (int i = 0;i < pointList.size(); i++){

            String pointId = (String) pointList.get(i).get("f_pointId");

            BESSbPzStruct sbPzStruct = sbPzStructCache.getCachedElementById(pointId);
            if (sbPzStruct == null) {
                continue;
            }

            Integer planId = Integer.valueOf((String) map.get("f_id"));

            String taskId = (String) map.get("taskId");

            String timename = (String) map.get("timename");

            typeDiPointMap.put("F_CRDATE",F_CRDATE);

            typeDiPointMap.put("F_PLANID",planId);

            typeDiPointMap.put("F_TASKID",taskId);

            typeDiPointMap.put("F_TIMENAME",timename);

            typeDiPointMap.put("F_GUID","0");

            typeDiPointMap.put("F_STRUCT_ID",sbPzStruct.getF_id());

            typeDiPointMap.put("F_SYS_NAME_OLD",sbPzStruct.getF_sys_name_old());

            typeDiPointMap.put("F_SYS_NAME",sbPzStruct.getF_sys_name());

            typeDiPointMap.put("F_INIT_VAL",sbPzStruct.getF_init_val());

            InsertDiPointHistoryInfo(typeDiPointMap);

        }
    }

    public void InsertDiPointHistoryInfo(Map<String,Object> typeDiPointMap){

        String F_ID = UUIDUtil.getRandom32BeginTimePK();

        typeDiPointMap.put("F_ID",F_ID);

        sysJobPlanMapper.InsertPointHistoryInfo(typeDiPointMap);

    }

    //执行定时同步设备树
    public void executeTimeTaskSyncInfo(SysJob sysJob) throws UnsupportedEncodingException, RemoteException, ServiceException {

        System.out.println("定时同步设备树走了***********************************:sysJob.name"+sysJob.getJobName());
        List<Map<String,Object>> infoList = new ArrayList<>();

        String syncId = sysJob.getSyncId();

        Map<String,Object> syncMap = sysJobPlanMapper.selectTimeTaskSyncInfomation(syncId);
        if (syncMap == null) {
            return;
        }

        if ("0".equals(syncMap.get("f_status"))) {//是否执行(1执行 0不执行)
            return;
        }

        List<Map<String,Object>> sbList = sysJobPlanMapper.queryTimeTaskSyncSbList(syncId);

        if (null == sbList){
            return;
        }else {
            for (Map<String, Object> sbInfo : sbList) {
                ISSPReturnObject returnObject = new ISSPReturnObject();

                String fPointType = sbInfo.get("f_point_type").toString();
                String fSysName = sbInfo.get("f_point_name").toString();

                //点位类型 2:DDC  3:IP路由器  26:能耗采集
                //判断点位类型 DDC和IP路由器用的一个同步接口 synchronizeDDC
                if ("3".equals(fPointType) || "2".equals(fPointType)) {
                    JSONObject object = new JSONObject();
                    object.put("old_f_sys_name",fSysName);
                    object.put("f_type",fPointType);

                    returnObject = besSbdyService.synchronizeDDC(object);

                } else if ("26".equals(fPointType)) {
                //能耗采集用的同步接口 synEnergyCollector
                    returnObject = enerEquipmentService.synEnergyCollector(fSysName);

                }

                System.out.println("定时同步设备树任务: " + sysJob.getJobName() + "------------>" + fSysName  + returnObject.getMsg());
            }
        }
    }
}
