import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MusicTrackStats {

  // Mapper: emit "L|1" for listens, "S|1" for skips
  public static class TrackEventMapper
       extends Mapper<LongWritable, Text, Text, Text> {
    private final Text trackId = new Text();
    private final Text tagAndCount = new Text();

    @Override
    public void map(LongWritable key, Text value, Context ctx)
        throws IOException, InterruptedException {
      String line = value.toString();
      if (line.startsWith("userId")) return;  // skip header
      String[] parts = line.split(",");
      if (parts.length < 3) return;

      String tId = parts[1].trim();
      String event = parts[2].trim().toLowerCase();

      trackId.set(tId);
      if (event.equals("listen")) {
        tagAndCount.set("L|1");
        ctx.write(trackId, tagAndCount);
      } else if (event.equals("skip")) {
        tagAndCount.set("S|1");
        ctx.write(trackId, tagAndCount);
      }
    }
  }

  // Reducer (and Combiner): sum listens and skips per track
  public static class StatsReducer
       extends Reducer<Text, Text, Text, Text> {
    private final Text result = new Text();

    @Override
    public void reduce(Text key, Iterable<Text> vals, Context ctx)
        throws IOException, InterruptedException {
      int listenSum = 0;
      int skipSum = 0;

      for (Text t : vals) {
        String[] parts = t.toString().split("\\|");
        if (parts[0].equals("L")) {
          listenSum += Integer.parseInt(parts[1]);
        } else if (parts[0].equals("S")) {
          skipSum += Integer.parseInt(parts[1]);
        }
      }
      // output format: trackId \t listens \t skips
      result.set(listenSum + "\t" + skipSum);
      ctx.write(key, result);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: MusicTrackStats <inputPath> <outputPath>");
      System.exit(2);
    }

    Configuration conf = new Configuration();
    Job job = Job.getInstance(conf, "Music Track Statistics");
    job.setJarByClass(MusicTrackStats.class);

    job.setMapperClass(TrackEventMapper.class);
    job.setCombinerClass(StatsReducer.class);     // use reducer logic as combiner
    job.setReducerClass(StatsReducer.class);

    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
