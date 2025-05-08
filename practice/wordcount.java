import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;

import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class WordCount{

    public static class TokenizerMapper extends Mapper <Object, Text. Text, IntWritable>{

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();
        @Override
        public void map(Object key, Text value, Context context) throws IOException,InterruptedException {
            StringTokenizer itr = new StringTokenizer(value.toString().toLowerCase());
            while(itr.hasMoreTokens()){
                word.set(itr.nextToken().replaceAll("\\W+". ""));
                if(!word.toString().isEmpty()){
                    context.write(word,one);
                }
            }

        }
    }

    public static class SumReducer extends Reducer<Text, IntWritable, Text, IntWritable>{
        private IntWritable result = new IntWritable();
        @override
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException{
            itn sum = 0;
            for(IntWritable val: values){
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }

    public static void main(String[] args) {
        if(args.length != 2){
            System.err.println("Usage: WordCount <input path> <output path>");
            System.exit(-1);
        }
        
    }
}