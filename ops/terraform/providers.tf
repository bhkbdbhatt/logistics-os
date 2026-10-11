terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Backend is deliberately local in the scaffold. Environment teams may switch to
  # an S3 + DynamoDB-lock backend; the rest of this module is backend-agnostic.
  backend "local" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = var.tags
  }
}