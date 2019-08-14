package jenkins.wback.plugin.listeners;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.Builder;
import jenkins.wback.plugin.HttpRequest;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @Pacekage: jenkins.wback.plugin.listeners
 * @Author: goosby.liu
 * @Date: 2019/01/21 15:14
 * @Version: 14
 * @Description: jenkins.wback.plugin
 **/
@Extension
public class WriteBackJobStatusListener extends RunListener<Run<?, ?>> {
    private final Logger logger = Logger.getLogger(WriteBackJobStatusListener.class.getName());
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final String TP_PERFORNAMCE_URL = "http://qa.99bill.net:8090/calculates/";

    public WriteBackJobStatusListener() {
    }

    public WriteBackJobStatusListener(Class<Run<?, ?>> targetType) {
        super(targetType);
    }

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {

    }

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        final String buildResult = run.getResult() == null ? "UNKNOWN" : run.getResult().toString();
        logger.info("start calc pts,job name is: " + run.getParent().getName() + " ;job status is: " + buildResult);
        //获取参数值
        String groupName = null;
        String startTime = null;
        String id = null;
        //String isWriteBack = null;
        try {
            id = run.getEnvironment(listener).get("ID_TA_TASK");
            groupName = run.getEnvironment(listener).get("group_name");
            startTime = run.getEnvironment(listener).get("startTime");
            //isWriteBack = run.getEnvironment(listener).get("isWriteBack");
        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Get job parameter Exception ", e);
        }
        String endTime = simpleDateFormat.format(new Date());
        try {
            endTime = URLEncoder.encode(endTime, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        //2:执行陈功，3执行失败
        final String status = "SUCCESS".equals(buildResult) ? "2" : "3";
        //根据ID查询该任务的实际结束时间，若不存在实际结束时间，则进行TPS的计算。若存在实际结束时间，则不计算。
        final String wirteStatus = this.checkIsCallBacked(id, groupName, startTime);
        logger.info("==> Step 1: Check test has called back? response is: " + wirteStatus + " ,test id is: " + id);
        //1表示未回写过状态，0表示回写过状态
        if (StringUtils.isNotEmpty(wirteStatus) && "1".equals(wirteStatus)) {
            //回写测试结果状态
            final String updateTestStatus = this.updateTestStatus(id, endTime, status,groupName);
            logger.info("==> Step 2: Update test status ,service response is: " + updateTestStatus + " ,test id is: " + id);
            //如果回写测试状态成功，则计算tps
            if (StringUtils.isNotEmpty(updateTestStatus) && !"0".equals(updateTestStatus)) {
                String collectionName = groupName + "_" + startTime;
                logger.info("==> Step 3: call back aggr service,calc tps and responsetime");
                String calcResult = this.calcTestResult(collectionName, groupName, startTime);
                logger.info("==> Aggr Service response message is: " + calcResult);
            }
        }
    }


    /**
     * Update the build status and duration.
     */
    @Override
    public void onFinalized(final Run<?, ?> run) {

    }

    /**
     * 检查该测试任务是否已经回写计算出TPS等指标，避免重复。
     *
     * @param id
     * @return 1：未回写过状态；其它：回写过状态
     */
    private String checkIsCallBacked(String id, String groupName, String testTime) {
        String queryTaskHis_url = TP_PERFORNAMCE_URL + "nfTaskHis/queryTaskHis?id=" + id +
                "&groupName=" + groupName + "&testTime=" + testTime;
        logger.info("Step 1: checkIsCallBacked, url is: " + queryTaskHis_url);
        return HttpRequest.sendGet(queryTaskHis_url);
    }

    /**
     * 回调计算服务统计TPS，耗时等指标
     *
     * @param collectionName
     * @return
     */
    private String calcTestResult(String collectionName, String groupName, String testTime) {
        String calcUrl = TP_PERFORNAMCE_URL + "insert/" + collectionName +
                "?groupName=" + groupName + "&testTime=" + testTime;
        logger.info("Step 3: calcTestResult, url is: " + calcUrl);
        return HttpRequest.sendGet(calcUrl);
    }

    /**
     * 回调服务 更新测试结果状态等信息
     *
     * @param id
     * @param endTime
     * @param status
     * @return
     */
    private String updateTestStatus(String id, String endTime, String status,String groupName) {
        String updateStatusUrl = TP_PERFORNAMCE_URL + "nfTaskHis/updateNfTaskHis?id="
                + id + "&endTime=" + endTime + "&status=" + status +"&groupName=" + groupName;
        logger.info("Step 2: updateStatus, url is: " + updateStatusUrl);
        return HttpRequest.sendGet(updateStatusUrl);
    }
}
