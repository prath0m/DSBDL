import java.io.*;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MovieRecommender {

  // Mapper for ratings.csv: userId,movieId,rating,timestamp
  public static class RatingsMapper
       extends Mapper<LongWritable, Text, IntWritable, Text> {
    private IntWritable movieId = new IntWritable();
    private Text outVal = new Text();

    @Override
    public void map(LongWritable key, Text line, Context ctx)
        throws IOException, InterruptedException {
      // skip header
      if (key.get()==0 && line.toString().contains("userId")) return;
      String[] parts = line.toString().split(",");
      // userId, movieId, rating, timestamp
      if (parts.length < 3) return;
      try {
        movieId.set(Integer.parseInt(parts[1]));
        outVal.set("R|" + parts[2]);    // tag as Rating
        ctx.write(movieId, outVal);
      } catch (NumberFormatException e) { /* skip bad record */ }
    }
  }

  // Mapper for movies.csv: movieId,title,genres
  public static class MoviesMapper
       extends Mapper<LongWritable, Text, IntWritable, Text> {
    private IntWritable movieId = new IntWritable();
    private Text outVal = new Text();

    @Override
    public void map(LongWritable key, Text line, Context ctx)
        throws IOException, InterruptedException {
      // skip header
      if (key.get()==0 && line.toString().startsWith("movieId")) return;
      // handle commas inside quotes by a simple regex split
      String[] parts = line.toString().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
      if (parts.length < 2) return;
      try {
        movieId.set(Integer.parseInt(parts[0]));
        String title = parts[1].replaceAll("^\"|\"$", ""); // strip quotes
        outVal.set("M|" + title);    // tag as Movie
        ctx.write(movieId, outVal);
      } catch (NumberFormatException e) { /* skip bad record */ }
    }
  }

  // Reducer: receives all values tagged either "M|title" or "R|rating"
  public static class JoinReducer
       extends Reducer<IntWritable, Text, Text, DoubleWritable> {
    private Text outTitle = new Text();
    private DoubleWritable outAvg = new DoubleWritable();

    @Override
    public void reduce(IntWritable key, Iterable<Text> vals, Context ctx)
        throws IOException, InterruptedException {
      String title = null;
      double sum = 0;
      int count = 0;

      for (Text t : vals) {
        String s = t.toString();
        if (s.startsWith("M|")) {
          title = s.substring(2);
        } else if (s.startsWith("R|")) {
          sum += Double.parseDouble(s.substring(2));
          count++;
        }
      }

      if (title != null && count > 0) {
        double avg = sum / count;
        outTitle.set(title);
        outAvg.set(avg);
        ctx.write(outTitle, outAvg);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.err.println("Usage: MovieRecommender <movies.csv dir> <ratings.csv dir> <output>");
      System.exit(2);
    }
    Configuration conf = new Configuration();
    Job job = Job.getInstance(conf, "Movie Recommendations");
    job.setJarByClass(MovieRecommender.class);

    // Set Reducer
    job.setReducerClass(JoinReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(DoubleWritable.class);

    // Multiple inputs
    Path moviesInput = new Path(args[0]);
    Path ratingsInput = new Path(args[1]);
    MultipleInputs.addInputPath(job, moviesInput,
        TextInputFormat.class, MoviesMapper.class);
    MultipleInputs.addInputPath(job, ratingsInput,
        TextInputFormat.class, RatingsMapper.class);

    FileOutputFormat.setOutputPath(job, new Path(args[2]));

    // (Optional) use combiner to pre-aggregate ratings
    job.setCombinerClass(JoinReducer.class);

    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
