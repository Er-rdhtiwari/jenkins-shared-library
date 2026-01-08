bucket         = "rdh-terraform-state"
key            = "platform-infra/terraform.tfstate"
region         = "us-west-2"
dynamodb_table = "rdh-terraform-locks"
encrypt        = true
