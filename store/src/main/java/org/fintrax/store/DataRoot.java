package org.fintrax.store;

import lombok.Data;
import org.fintrax.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DataRoot {
    private List<BankAccount> accounts = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<Label> labels = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();
    private List<SyncLog> syncLogs = new ArrayList<>();
    private List<ActivityLog> activityLogs = new ArrayList<>();
    private Map<String, AppSetting> settings = new HashMap<>();
}
