package package05.secondsort;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

/**
 * @author zengzhaozheng
 *
 * 用途说明：二次排序mapreduce
 * 需求描述:
 * ---------------输入-----------------
 * sort1,1
 * sort2,3
 * sort2,77
 * sort2,54
 * sort1,2
 * sort6,22
 * sort6,221
 * sort6,20
 * ---------------目标输出---------------
 * sort1 1,2
 * sort2 3,54,77
 * sort6 20,22,221
 */
public class SecondSortMR extends Configured  implements Tool {
    public static class SortMapper extends Mapper<Text, Text, CombinationKey, IntWritable> {
        //---------------------------------------------------------
        /**
         * 这里特殊要说明一下，为什么要将这些变量写在map函数外边。
         * 对于分布式的程序，我们一定要注意到内存的使用情况，对于mapreduce框架，
         * 每一行的原始记录的处理都要调用一次map函数，假设，此个map要处理1亿条输
         * 入记录，如果将这些变量都定义在map函数里边则会导致这4个变量的对象句柄编
         * 程非常多（极端情况下将产生4*1亿个句柄，当然java也是有自动的gc机制的，
         * 一定不会达到这么多，但是会浪费很多时间去GC），导致栈内存被浪费掉。我们将其写在map函数外边，
         * 顶多就只有4个对象句柄。
         */
        CombinationKey combinationKey = new CombinationKey();
        Text sortName = new Text();
        IntWritable score = new IntWritable();
        String[] inputString = null;
        //---------------------------------------------------------
        @Override
        protected void map(Text key, Text value, Context context)
                throws IOException, InterruptedException {
            //过滤非法记录
            if(key == null || value == null || key.toString().equals("")
                    || value.equals("")){
                return;
            }
            score.set(Integer.parseInt(value.toString()));
            combinationKey.setFirstKey(key.toString());
            combinationKey.setSecondKey(Integer.parseInt(value.toString()));
            //map输出
            context.write(combinationKey, score);
        }
    }

    public static class SortReducer extends
            Reducer<CombinationKey, IntWritable, Text, Text> {
        StringBuffer sb = new StringBuffer();
        Text sore = new Text();
        /**
         * 这里要注意一下reduce的调用时机和次数:reduce每处理一个分组的时候会调用一
         * 次reduce函数。也许有人会疑问，分组是什么？看个例子就明白了：
         * eg:
         * {{sort1,{1,2}},{sort2,{3,54,77}},{sort6,{20,22,221}}}
         * 这个数据结果是分组过后的数据结构，那么一个分组分别为{sort1,{1,2}}、
         * {sort2,{3,54,77}}、{sort6,{20,22,221}}
         */
        @Override
        protected void reduce(CombinationKey key,
                              Iterable<IntWritable> value, Context context)
                throws IOException, InterruptedException {
            sb.delete(0, sb.length());//先清除上一个组的数据
            Iterator<IntWritable> it = value.iterator();

            while(it.hasNext()){
                sb.append(it.next()+",");
            }
            //去除最后一个逗号
            if(sb.length()>0){
                sb.deleteCharAt(sb.length()-1);
            }
            sore.set(sb.toString());
            context.write(new Text(key.getFirstKey()),sore);
        }
    }

    Configuration configuration = null;
    @Override
    public void setConf(Configuration conf) {
        System.setProperty("hadoop.home.dir", "/Users/dailiang/Documents/Software/hadoop-2.10.0");
        configuration = new Configuration();
        configuration.set("mapreduce.framework.name", "local");
        configuration.set("fs.defaultFS","hdfs://localhost:9000");
        super.setConf(configuration);
    }

    @Override
    public int run(String[] args) throws Exception {
        String inputFile = "/Users/dailiang/Documents/second22";
        String outputDir = "/Users/dailiang/Documents/secondOut22";

        Configuration conf=getConf(); //获得配置文件对象
        FileSystem fs = FileSystem.get(conf);


        Job job=Job.getInstance(conf,"SoreSort");
        job.setJarByClass(SecondSortMR.class);

//        FileInputFormat.addInputPath(job, new Path(args[0])); //设置map输入文件路径
//        FileOutputFormat.setOutputPath(job, new Path(args[1])); //设置reduce输出文件路径

        FileInputFormat.addInputPath(job, new Path(inputFile));
        FileOutputFormat.setOutputPath(job, new Path(outputDir));
        if (fs.exists(new Path(outputDir))) {
            fs.delete(new Path(outputDir), true);
        }

        job.setMapperClass(SortMapper.class);
        job.setReducerClass(SortReducer.class);

        job.setPartitionerClass(DefinedPartition.class); //设置自定义分区策略

        job.setGroupingComparatorClass(DefinedGroupSort.class); //设置自定义分组策略
        job.setSortComparatorClass(DefinedComparator.class); //设置自定义二次排序策略

        job.setInputFormatClass(KeyValueTextInputFormat.class); //设置文件输入格式
        job.setOutputFormatClass(TextOutputFormat.class);//使用默认的output格式

        //设置map的输出key和value类型
        job.setMapOutputKeyClass(CombinationKey.class);
        job.setMapOutputValueClass(IntWritable.class);

        //设置reduce的输出key和value类型
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.waitForCompletion(true);
        return job.isSuccessful()?0:1;
    }

    public static void main(String[] args) {
        try {
            int returnCode =  ToolRunner.run(new SecondSortMR(),args);
            System.exit(returnCode);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
