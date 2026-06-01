package me.serenityline.api.user.service.deletion;

public record AccountHardDeletionResult(
        int candidatesFound,
        int ownerGroupsDeleted,
        int collaboratorUsersDeleted,
        int rowsDeleted
) {

    public AccountHardDeletionResult {
        validateNonNegative(candidatesFound, "candidatesFound");
        validateNonNegative(ownerGroupsDeleted, "ownerGroupsDeleted");
        validateNonNegative(collaboratorUsersDeleted, "collaboratorUsersDeleted");
        validateNonNegative(rowsDeleted, "rowsDeleted");
    }

    private static void validateNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException("accountHardDeletion.%s.negative".formatted(fieldName));
        }
    }

    public int deletedSubjects() {
        return ownerGroupsDeleted + collaboratorUsersDeleted;
    }

    public boolean hasWork() {
        return candidatesFound > 0 || rowsDeleted > 0;
    }
}