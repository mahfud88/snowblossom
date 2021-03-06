package snowblossom.shackleton;

import java.io.PrintStream;
import snowblossom.lib.*;
import snowblossom.proto.*;

import java.util.TreeSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;


import java.util.logging.Level;
import java.util.logging.Logger;

import duckutil.LRUCache;


public class VoteTracker extends Thread
{
	private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  private volatile boolean startup=true;
  private final Shackleton shackleton;
  public static final int LOOK_BACK=1000;

  LRUCache<ChainHash, CoinbaseExtras> extra_map=new LRUCache<>(2000);
  LRUCache<ChainHash, ChainHash> prev_map=new LRUCache<>(2000);

  public VoteTracker(Shackleton shackleton)
  {
    this.shackleton = shackleton;
    setDaemon(true);
    setName("VoteTracker");
  }

  public void run()
  {
    while(true)
    {
      try
      {
        runInner();
      }
      catch(Throwable t)
      {
        logger.log(Level.WARNING, "Exception in VoteTracker: " + t);
      }
      try
      {
        sleep(60000);
      }
      catch(Throwable t){}

    }

  
  }

  private void runInner() throws Exception
  {
    NetworkParams params = shackleton.getParams();
    NodeStatus node_status = shackleton.getStub().getNodeStatus(nr());

    update(node_status);
    startup=false;

  }

  private TreeMap<Integer, VoteCount> update(NodeStatus ns)
  {
    TreeMap<Integer, VoteCount> vote_map=new TreeMap<>();

    if (ns.getHeadSummary().getHeader().getBlockHeight() < LOOK_BACK) return vote_map;
    ChainHash prev = new ChainHash(ns.getHeadSummary().getHeader().getSnowHash());
    int blocks = 0;

    while(blocks < LOOK_BACK)
    {
      ChainHash h = prev;
      CoinbaseExtras extras = null;
      if ((!prev_map.containsKey(h)) || (!extra_map.containsKey(h)))
      {
        logger.log(Level.FINE, "Fetching block for vote: " + h);
        Block blk = shackleton.getStub().getBlock(RequestBlock.newBuilder().setBlockHash(h.getBytes()).build());

        extras = TransactionUtil.getInner(blk.getTransactions(0)).getCoinbaseExtras();

        prev = new ChainHash(blk.getHeader().getPrevBlockHash());
        prev_map.put(h, prev);
        extra_map.put(h, extras);
      }
      else
      {
        extras = extra_map.get(h);
        prev = prev_map.get(h);
      }
      
      updateVoteMap(vote_map, extras);

      blocks++;
    }


    return vote_map;
    
  }


  public void getVotingReport(NodeStatus ns, PrintStream out)
  {
    if (startup)
    {
      out.println("Reading blocks for votes");
      return;
    }

    TreeMap<Integer, VoteCount> vote_map = update(ns);

    for(Map.Entry<Integer, VoteCount> me : vote_map.entrySet())
    {
      out.println(String.format("Issue %d: %s", me.getKey(), me.getValue().toString()));
    }

  }

  public NullRequest nr()
  {
    return NullRequest.newBuilder().build();
  }

  public class VoteCount
  {
    int vote_yes;
    int vote_no;
    int vote_both;

    @Override
    public String toString()
    {
      return String.format("Yes: %d, No: %d, Both: %d", vote_yes, vote_no, vote_both);
    }

  }

  private void updateVoteMap(Map<Integer, VoteCount> vote_map, CoinbaseExtras extras)
  {
    Set<Integer> app = new TreeSet<>();
    Set<Integer> rej = new TreeSet<>();
    Set<Integer> all = new TreeSet<>();

    app.addAll(extras.getMotionsApprovedList());
    rej.addAll(extras.getMotionsRejectedList());
    all.addAll(app);
    all.addAll(rej);

    for(int issue : all)
    {
      VoteCount vc = vote_map.get(issue);
      if (vc == null)
      {
        vc = new VoteCount();
        vote_map.put(issue, vc);
      }
      if (app.contains(issue))
      {
        if (rej.contains(issue))
        {
          vc.vote_both++;
        }
        else
        {
          vc.vote_yes++;
        }
      }
      else if (rej.contains(issue))
      {
        vc.vote_no++;
      }

    }

  }

}
