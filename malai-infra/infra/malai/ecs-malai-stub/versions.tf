terraform {
  required_version = "= 1.14.9"

  backend "s3" {
    key     = "ecs-malai-stub.tfstate"
    encrypt = true
  }
}

provider "aws" {
  region = var.region

  assume_role {
    role_arn = var.assume_role.role_arn
  }
}
