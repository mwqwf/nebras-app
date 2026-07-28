package com.nebras.mobile.core.error

/**
 * الصفّ الأساس لكلّ الإخفاقات في طبقة الـ domain.
 * مقابل `core/error/failures.dart` (كان يعتمد `Equatable` — `data class`
 * في Kotlin يوفّر المساواة البنيويّة نفسها مجّاناً).
 */
sealed class Failure(
    open val message: String,
    open val statusCode: Int? = null,
)

data class NetworkFailure(
    override val message: String = "Network error occurred",
) : Failure(message)

data class ServerFailure(
    override val message: String = "Server error occurred",
    override val statusCode: Int? = null,
) : Failure(message, statusCode)

data class ClientFailure(
    override val message: String = "Client error occurred",
    override val statusCode: Int? = null,
) : Failure(message, statusCode)

data class TimeoutFailure(
    override val message: String = "Request timeout",
) : Failure(message)

data class CancellationFailure(
    override val message: String = "Request cancelled",
) : Failure(message)

data class CacheFailure(
    override val message: String = "Cache error occurred",
) : Failure(message)

data class UnknownFailure(
    override val message: String = "An unknown error occurred",
) : Failure(message)

data class ValidationFailure(
    override val message: String = "Validation error",
) : Failure(message)

data class NotFoundFailure(
    override val message: String = "Resource not found",
) : Failure(message, 404)

data class UnauthorizedFailure(
    override val message: String = "Unauthorized access",
) : Failure(message, 401)

data class ForbiddenFailure(
    override val message: String = "Access forbidden",
) : Failure(message, 403)
