package com.amit.jobagent.jobsource;

record RunMetrics(int discovered,int created,int updated,int unchanged,int duplicates,int failed,int removed){
    RunMetrics withRemoved(int value){return new RunMetrics(discovered,created,updated,unchanged,duplicates,failed,value);}
}
