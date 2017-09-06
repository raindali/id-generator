package butterfly.assigner.impl;

import butterfly.assigner.WorkerIdAssigner;
import butterfly.util.NetUtils;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/**
 * 启动时通过Zookeeper获取workerId
 * @author Ricky Fung
 */
public class ZookeeperWorkerIdAssigner implements WorkerIdAssigner {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private String zkAddress;
    private String namespace;
    private int sessionTimeout;
    private int connectTimeout;
    private ZkClient zkClient;
    private static final String PREFIX = "worker_";

    public ZookeeperWorkerIdAssigner(String zkAddress, String namespace) {
        this(zkAddress, 60 * 1000, 5000, namespace);
    }

    public ZookeeperWorkerIdAssigner(String zkAddress, int sessionTimeout, int connectTimeout, String namespace) {
        this.zkAddress = zkAddress;
        this.sessionTimeout = sessionTimeout;
        this.connectTimeout = connectTimeout;
        this.namespace = namespace;
        initZooKeeper(this.zkAddress, this.sessionTimeout, this.connectTimeout);
    }

    @Override
    public long getWorkId() {

        //创建根节点
        zkClient.createPersistent(namespace, true);

        String hostInfo = getHostInfo();
        String path;    //worker node path
        if(namespace.endsWith("/")) {
            path = namespace + PREFIX;
        } else {
            path = namespace + "/"+ PREFIX;
        }
        logger.info("create node path:{}, data:{}", path, hostInfo);
        String node = zkClient.createEphemeralSequential(path, hostInfo);
        logger.info("create node path:{}, result:{}", path, node);

        List<String> children = zkClient.getChildren(namespace);
        if(children==null || children.isEmpty()) {
            return 0;
        }
        Collections.sort(children);
        int id = 0;
        for (int i=0; i<children.size(); i++) {
            if(node.endsWith(children.get(i))) {
                id = i;
                break;
            }
        }
        return id;
    }

    public void close() {
        zkClient.close();
    }

    private void initZooKeeper(String zkAddress, int sessionTimeout, int connectTimeout) {
        logger.info("init ZooKeeper address:{} sessionTimeout:{}, connectTimeout:{}", zkAddress, sessionTimeout, connectTimeout);
        // 连接到ZK服务，多个可以用逗号分割写
        zkClient = new ZkClient(zkAddress, sessionTimeout, connectTimeout);
        logger.info("init ZooKeeper address:{} over!", zkAddress);
    }

    private String getHostInfo() {
        InetAddress address = NetUtils.getLocalAddress();
        String ip = "N/A";
        if(address!=null) {
            ip = NetUtils.getLocalAddress().getHostAddress();
        }
        int pid = NetUtils.getPid();
        return String.format("%s:%s", ip, pid);
    }

    public static void main(String[] args) {

        ZookeeperWorkerIdAssigner assigner = new ZookeeperWorkerIdAssigner("127.0.0.1:2181", "/pg/uid/worker");
        System.out.println(assigner.getWorkId());
        assigner.close();
    }
}