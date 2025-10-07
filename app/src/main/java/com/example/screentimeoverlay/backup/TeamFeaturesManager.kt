package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Team features manager for shared goals and accountability
 * among family members or team members
 */
class TeamFeaturesManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("team_features", Context.MODE_PRIVATE)
    private val teamManager = TeamManager()
    private val goalManager = GoalManager()
    private val accountabilityManager = AccountabilityManager()
    
    /**
     * Create a new team
     */
    fun createTeam(teamInfo: TeamInfo, creator: TeamMember): TeamCreationResult {
        val result = try {
            val team = Team(
                id = UUID.randomUUID().toString(),
                name = teamInfo.name,
                description = teamInfo.description,
                members = listOf(creator),
                goals = emptyList(),
                createdAt = Date(),
                isActive = true
            )
            
            teamManager.createTeam(team)
            
            TeamCreationResult(
                success = true,
                message = "Team created successfully",
                team = team
            )
        } catch (e: Exception) {
            TeamCreationResult(
                success = false,
                message = "Failed to create team: ${e.message}",
                team = null
            )
        }
        
        return result
    }
    
    /**
     * Join an existing team
     */
    fun joinTeam(teamId: String, member: TeamMember): JoinResult {
        val result = try {
            val team = teamManager.getTeam(teamId)
            if (team == null) {
                return JoinResult(
                    success = false,
                    message = "Team not found",
                    team = null
                )
            }
            
            val updatedTeam = team.copy(members = team.members + member)
            teamManager.updateTeam(updatedTeam)
            
            JoinResult(
                success = true,
                message = "Successfully joined team",
                team = updatedTeam
            )
        } catch (e: Exception) {
            JoinResult(
                success = false,
                message = "Failed to join team: ${e.message}",
                team = null
            )
        }
        
        return result
    }
    
    /**
     * Set shared goals for the team
     */
    fun setSharedGoals(teamId: String, goals: List<SharedGoal>): GoalSettingResult {
        val result = try {
            val team = teamManager.getTeam(teamId)
            if (team == null) {
                return GoalSettingResult(
                    success = false,
                    message = "Team not found",
                    goals = emptyList()
                )
            }
            
            val updatedTeam = team.copy(goals = goals)
            teamManager.updateTeam(updatedTeam)
            
            // Set up goal tracking for all team members
            goals.forEach { goal ->
                goalManager.setupGoalTracking(teamId, goal)
            }
            
            GoalSettingResult(
                success = true,
                message = "Shared goals set successfully",
                goals = goals
            )
        } catch (e: Exception) {
            GoalSettingResult(
                success = false,
                message = "Failed to set shared goals: ${e.message}",
                goals = emptyList()
            )
        }
        
        return result
    }
    
    /**
     * Get team progress summary
     */
    fun getTeamProgressSummary(teamId: String): TeamProgressSummary {
        val team = teamManager.getTeam(teamId)
        if (team == null) {
            return TeamProgressSummary(
                teamId = teamId,
                teamName = "Unknown",
                members = emptyList(),
                goals = emptyList(),
                overallProgress = 0,
                memberProgress = emptyMap(),
                goalProgress = emptyMap(),
                achievements = emptyList(),
                recommendations = emptyList()
            )
        }
        
        val memberProgress = getMemberProgress(team.members)
        val goalProgress = getGoalProgress(team.goals)
        val overallProgress = calculateOverallProgress(memberProgress, goalProgress)
        val achievements = getTeamAchievements(team)
        val recommendations = generateTeamRecommendations(team, memberProgress, goalProgress)
        
        return TeamProgressSummary(
            teamId = teamId,
            teamName = team.name,
            members = team.members,
            goals = team.goals,
            overallProgress = overallProgress,
            memberProgress = memberProgress,
            goalProgress = goalProgress,
            achievements = achievements,
            recommendations = recommendations
        )
    }
    
    /**
     * Get member accountability report
     */
    fun getMemberAccountabilityReport(teamId: String, memberId: String): AccountabilityReport {
        val team = teamManager.getTeam(teamId)
        if (team == null) {
            return AccountabilityReport(
                memberId = memberId,
                teamId = teamId,
                memberName = "Unknown",
                goals = emptyList(),
                progress = emptyMap(),
                accountabilityScore = 0,
                recommendations = emptyList()
            )
        }
        
        val member = team.members.find { it.id == memberId }
        if (member == null) {
            return AccountabilityReport(
                memberId = memberId,
                teamId = teamId,
                memberName = "Unknown",
                goals = emptyList(),
                progress = emptyMap(),
                accountabilityScore = 0,
                recommendations = emptyList()
            )
        }
        
        val memberGoals = getMemberGoals(teamId, memberId)
        val progress = getMemberGoalProgress(teamId, memberId, memberGoals)
        val accountabilityScore = calculateAccountabilityScore(progress)
        val recommendations = generateAccountabilityRecommendations(progress, memberGoals)
        
        return AccountabilityReport(
            memberId = memberId,
            teamId = teamId,
            memberName = member.name,
            goals = memberGoals,
            progress = progress,
            accountabilityScore = accountabilityScore,
            recommendations = recommendations
        )
    }
    
    /**
     * Get team leaderboard
     */
    fun getTeamLeaderboard(teamId: String): TeamLeaderboard {
        val team = teamManager.getTeam(teamId)
        if (team == null) {
            return TeamLeaderboard(
                teamId = teamId,
                teamName = "Unknown",
                leaderboard = emptyList(),
                categories = emptyList()
            )
        }
        
        val leaderboard = calculateLeaderboard(team.members)
        val categories = getLeaderboardCategories()
        
        return TeamLeaderboard(
            teamId = teamId,
            teamName = team.name,
            leaderboard = leaderboard,
            categories = categories
        )
    }
    
    /**
     * Get team challenges
     */
    fun getTeamChallenges(teamId: String): List<TeamChallenge> {
        val team = teamManager.getTeam(teamId)
        if (team == null) return emptyList()
        
        val challenges = mutableListOf<TeamChallenge>()
        
        // Screen time reduction challenge
        challenges.add(
            TeamChallenge(
                id = "screen_time_reduction",
                title = "Screen Time Reduction",
                description = "Reduce team average screen time by 1 hour",
                type = ChallengeType.SCREEN_TIME_REDUCTION,
                target = "1 hour reduction",
                currentProgress = "0.5 hours",
                completionPercentage = 50,
                endDate = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)),
                participants = team.members.size,
                rewards = listOf("Team achievement badge", "Screen time insights")
            )
        )
        
        // Focus improvement challenge
        challenges.add(
            TeamChallenge(
                id = "focus_improvement",
                title = "Focus Improvement",
                description = "Improve team average focus score by 20%",
                type = ChallengeType.FOCUS_IMPROVEMENT,
                target = "20% improvement",
                currentProgress = "10% improvement",
                completionPercentage = 50,
                endDate = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14)),
                participants = team.members.size,
                rewards = listOf("Focus mastery badge", "Productivity tips")
            )
        )
        
        return challenges
    }
    
    /**
     * Get team insights and recommendations
     */
    fun getTeamInsights(teamId: String): TeamInsights {
        val team = teamManager.getTeam(teamId)
        if (team == null) {
            return TeamInsights(
                teamId = teamId,
                teamName = "Unknown",
                insights = emptyList(),
                recommendations = emptyList(),
                trends = emptyList(),
                overallScore = 0
            )
        }
        
        val insights = generateTeamInsights(team)
        val recommendations = generateTeamRecommendations(team, emptyMap(), emptyMap())
        val trends = analyzeTeamTrends(team)
        val overallScore = calculateTeamOverallScore(insights, trends)
        
        return TeamInsights(
            teamId = teamId,
            teamName = team.name,
            insights = insights,
            recommendations = recommendations,
            trends = trends,
            overallScore = overallScore
        )
    }
    
    private fun getMemberProgress(members: List<TeamMember>): Map<String, MemberProgress> {
        val progress = mutableMapOf<String, MemberProgress>()
        
        members.forEach { member ->
            val memberGoals = getMemberGoals("", member.id)
            val goalProgress = getMemberGoalProgress("", member.id, memberGoals)
            val accountabilityScore = calculateAccountabilityScore(goalProgress)
            
            progress[member.id] = MemberProgress(
                memberId = member.id,
                memberName = member.name,
                goals = memberGoals,
                goalProgress = goalProgress,
                accountabilityScore = accountabilityScore,
                lastActive = Date() // Mock value
            )
        }
        
        return progress
    }
    
    private fun getGoalProgress(goals: List<SharedGoal>): Map<String, GoalProgress> {
        val progress = mutableMapOf<String, GoalProgress>()
        
        goals.forEach { goal ->
            val completionRate = calculateGoalCompletionRate(goal)
            val memberProgress = getGoalMemberProgress(goal)
            
            progress[goal.id] = GoalProgress(
                goal = Goal(
                    id = goal.id,
                    name = goal.name,
                    type = goal.type,
                    targetValue = goal.target.toLongOrNull() ?: 0L,
                    startDate = Date(),
                    endDate = goal.targetDate,
                    isActive = goal.isActive
                ),
                currentUsageMs = 0L,
                progressPercentage = completionRate.toDouble(),
                isOverGoal = completionRate > 100,
                remainingMs = 0L
            )
        }
        
        return progress
    }
    
    private fun calculateOverallProgress(memberProgress: Map<String, MemberProgress>, goalProgress: Map<String, GoalProgress>): Int {
        val memberScores = memberProgress.values.map { it.accountabilityScore }
        val goalScores = goalProgress.values.map { it.progressPercentage }
        
        val averageMemberScore = if (memberScores.isNotEmpty()) memberScores.average() else 0.0
        val averageGoalScore = if (goalScores.isNotEmpty()) goalScores.average() else 0.0
        
        return ((averageMemberScore + averageGoalScore) / 2).toInt()
    }
    
    private fun getTeamAchievements(team: Team): List<TeamAchievement> {
        val achievements = mutableListOf<TeamAchievement>()
        
        // Mock achievements based on team data
        if (team.members.size >= 5) {
            achievements.add(
                TeamAchievement(
                    id = "large_team",
                    title = "Large Team",
                    description = "Team has 5 or more members",
                    type = AchievementType.TEAM_SIZE,
                    earnedDate = Date(),
                    points = 100
                )
            )
        }
        
        if (team.goals.size >= 3) {
            achievements.add(
                TeamAchievement(
                    id = "goal_setter",
                    title = "Goal Setter",
                    description = "Team has set 3 or more goals",
                    type = AchievementType.GOAL_SETTING,
                    earnedDate = Date(),
                    points = 150
                )
            )
        }
        
        return achievements
    }
    
    private fun generateTeamRecommendations(team: Team, memberProgress: Map<String, MemberProgress>, goalProgress: Map<String, GoalProgress>): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (team.members.size < 3) {
            recommendations.add("Consider inviting more members to increase accountability")
        }
        
        if (team.goals.isEmpty()) {
            recommendations.add("Set shared goals to improve team accountability")
        }
        
        val lowProgressMembers = memberProgress.values.filter { it.accountabilityScore < 50 }
        if (lowProgressMembers.isNotEmpty()) {
            recommendations.add("Some members need additional support to meet their goals")
        }
        
        return recommendations
    }
    
    private fun getMemberGoals(teamId: String, memberId: String): List<SharedGoal> {
        // This would retrieve member goals from the system
        return emptyList()
    }
    
    private fun getMemberGoalProgress(teamId: String, memberId: String, goals: List<SharedGoal>): Map<String, Int> {
        val progress = mutableMapOf<String, Int>()
        
        goals.forEach { goal ->
            // Mock progress calculation
            progress[goal.id] = (Math.random() * 100).toInt()
        }
        
        return progress
    }
    
    private fun calculateAccountabilityScore(progress: Map<String, Int>): Int {
        if (progress.isEmpty()) return 0
        
        val averageProgress = progress.values.average()
        return averageProgress.toInt()
    }
    
    private fun generateAccountabilityRecommendations(progress: Map<String, Int>, goals: List<SharedGoal>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val lowProgressGoals = progress.filter { it.value < 50 }
        if (lowProgressGoals.isNotEmpty()) {
            recommendations.add("Focus on improving progress for ${lowProgressGoals.size} goals")
        }
        
        val highProgressGoals = progress.filter { it.value >= 80 }
        if (highProgressGoals.isNotEmpty()) {
            recommendations.add("Great job! You're making excellent progress on ${highProgressGoals.size} goals")
        }
        
        return recommendations
    }
    
    private fun calculateLeaderboard(members: List<TeamMember>): List<LeaderboardEntry> {
        return members.map { member ->
            LeaderboardEntry(
                memberId = member.id,
                memberName = member.name,
                score = (Math.random() * 100).toInt(),
                rank = 0, // Will be set after sorting
                achievements = emptyList()
            )
        }.sortedByDescending { it.score }.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
    }
    
    private fun getLeaderboardCategories(): List<String> {
        return listOf("Overall Score", "Goal Completion", "Accountability", "Team Contribution")
    }
    
    private fun generateTeamInsights(team: Team): List<String> {
        val insights = mutableListOf<String>()
        
        insights.add("Team has ${team.members.size} members")
        insights.add("Team has set ${team.goals.size} shared goals")
        insights.add("Team was created on ${team.createdAt}")
        
        return insights
    }
    
    private fun analyzeTeamTrends(team: Team): List<String> {
        val trends = mutableListOf<String>()
        
        trends.add("Team activity is increasing")
        trends.add("Goal completion rate is improving")
        trends.add("Member engagement is high")
        
        return trends
    }
    
    private fun calculateTeamOverallScore(insights: List<String>, trends: List<String>): Int {
        val insightScore = insights.size * 10
        val trendScore = trends.size * 15
        return (insightScore + trendScore).coerceAtMost(100)
    }
    
    private fun calculateGoalCompletionRate(goal: SharedGoal): Int {
        // Mock calculation
        return (Math.random() * 100).toInt()
    }
    
    private fun getGoalMemberProgress(goal: SharedGoal): Map<String, Int> {
        // Mock member progress for the goal
        return emptyMap()
    }
    
    companion object {
        private const val TAG = "TeamFeaturesManager"
    }
}

// Data classes
data class TeamInfo(
    val name: String,
    val description: String,
    val isPrivate: Boolean = false
)

data class Team(
    val id: String,
    val name: String,
    val description: String,
    val members: List<TeamMember>,
    val goals: List<SharedGoal>,
    val createdAt: Date,
    val isActive: Boolean
)

data class TeamMember(
    val id: String,
    val name: String,
    val email: String,
    val role: TeamRole,
    val joinedAt: Date
)

data class SharedGoal(
    val id: String,
    val name: String,
    val description: String,
    val type: GoalType,
    val target: String,
    val targetDate: Date,
    val isActive: Boolean
)

data class TeamCreationResult(
    val success: Boolean,
    val message: String,
    val team: Team?
)

data class JoinResult(
    val success: Boolean,
    val message: String,
    val team: Team?
)

data class GoalSettingResult(
    val success: Boolean,
    val message: String,
    val goals: List<SharedGoal>
)

data class TeamProgressSummary(
    val teamId: String,
    val teamName: String,
    val members: List<TeamMember>,
    val goals: List<SharedGoal>,
    val overallProgress: Int,
    val memberProgress: Map<String, MemberProgress>,
    val goalProgress: Map<String, GoalProgress>,
    val achievements: List<TeamAchievement>,
    val recommendations: List<String>
)

data class MemberProgress(
    val memberId: String,
    val memberName: String,
    val goals: List<SharedGoal>,
    val goalProgress: Map<String, Int>,
    val accountabilityScore: Int,
    val lastActive: Date
)


data class TeamAchievement(
    val id: String,
    val title: String,
    val description: String,
    val type: AchievementType,
    val earnedDate: Date,
    val points: Int
)

data class AccountabilityReport(
    val memberId: String,
    val teamId: String,
    val memberName: String,
    val goals: List<SharedGoal>,
    val progress: Map<String, Int>,
    val accountabilityScore: Int,
    val recommendations: List<String>
)

data class TeamLeaderboard(
    val teamId: String,
    val teamName: String,
    val leaderboard: List<LeaderboardEntry>,
    val categories: List<String>
)

data class LeaderboardEntry(
    val memberId: String,
    val memberName: String,
    val score: Int,
    val rank: Int,
    val achievements: List<String>
)

data class TeamChallenge(
    val id: String,
    val title: String,
    val description: String,
    val type: ChallengeType,
    val target: String,
    val currentProgress: String,
    val completionPercentage: Int,
    val endDate: Date,
    val participants: Int,
    val rewards: List<String>
)

data class TeamInsights(
    val teamId: String,
    val teamName: String,
    val insights: List<String>,
    val recommendations: List<String>,
    val trends: List<String>,
    val overallScore: Int
)

enum class TeamRole {
    ADMIN,
    MEMBER,
    MODERATOR
}


enum class AchievementType {
    TEAM_SIZE,
    GOAL_SETTING,
    GOAL_COMPLETION,
    TEAM_COLLABORATION
}

enum class ChallengeType {
    SCREEN_TIME_REDUCTION,
    FOCUS_IMPROVEMENT,
    PRODUCTIVITY_INCREASE,
    HABIT_FORMATION
}

class TeamManager {
    fun createTeam(team: Team): Boolean {
        return true
    }
    
    fun getTeam(teamId: String): Team? {
        return null
    }
    
    fun updateTeam(team: Team): Boolean {
        return true
    }
}

class GoalManager {
    fun setupGoalTracking(teamId: String, goal: SharedGoal): Boolean {
        return true
    }
}

class AccountabilityManager {
    fun trackAccountability(teamId: String, memberId: String, goalId: String): Boolean {
        return true
    }
}
