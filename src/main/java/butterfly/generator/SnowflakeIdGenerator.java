package butterfly.generator;

import butterfly.assigner.WorkerIdAssigner;
import butterfly.domain.UidMetaData;

/**
 * Twitter Snowflake
 * 1. 41位为时间戳
 * 2. 10位workerId(10位的长度最多支持部署1024个节点）
 * 3. 12位自增序列号（12位顺序号支持每个节点每毫秒产生4096个ID序号）
 *
 * @author Ricky Fung
 */
public class SnowflakeIdGenerator implements IdGenerator {

    private long epoch;    //纪元

    public static final long WORKER_ID_BITS = 10L;   //机器标识位数
    public static final long SEQUENCE_BITS = 12L;  //毫秒内自增位

    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);   //机器ID最大值

    private static final long WORKER_ID_LEFT_SHIFT_BITS = SEQUENCE_BITS;    //机器ID偏左移12位

    private static final long TIMESTAMP_LEFT_SHIFT_BITS  = SEQUENCE_BITS + WORKER_ID_BITS; //时间毫秒左移22位

    private static final long SEQUENCE_MASK = (1 << SEQUENCE_BITS) - 1;

    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(WorkerIdAssigner workerIdAssigner, long epoch) {
        this(workerIdAssigner.getWorkId(), epoch);
    }

    public SnowflakeIdGenerator(long workerId, long epoch) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("workerId must in [0, %d]", MAX_WORKER_ID));
        }
        this.workerId = workerId;
        if(epoch<0 || epoch>System.currentTimeMillis()){
            throw new IllegalArgumentException(String.format("epoch must in (0, %d]", System.currentTimeMillis()));
        }
        this.epoch = epoch;
    }

    @Override
    public synchronized long getUid() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        if (lastTimestamp == timestamp) {
            if (0L == (++sequence & SEQUENCE_MASK)) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << TIMESTAMP_LEFT_SHIFT_BITS) | (workerId << WORKER_ID_LEFT_SHIFT_BITS) | sequence;
    }

    @Override
    public UidMetaData parseUid(long uid) {

        long ts = (uid >> TIMESTAMP_LEFT_SHIFT_BITS) + epoch;
        long worker = (uid >> WORKER_ID_LEFT_SHIFT_BITS) & ((1 << WORKER_ID_BITS) - 1);
        long seq = uid & SEQUENCE_MASK;

        UidMetaData metaData = new UidMetaData();
        metaData.setTimestamp(ts);
        metaData.setWorkerId(worker);
        metaData.setSequence(seq);
        return metaData;
    }

    private long waitUntilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }
}
