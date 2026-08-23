package com.amit.jobagent.application;import jakarta.validation.constraints.Size;public record RegeneratePackageRequest(boolean replaceUserEdited,@Size(max=500)String reason){}
