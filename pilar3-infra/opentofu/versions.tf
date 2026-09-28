terraform {
  required_version = ">= 1.0"

  # Estado remoto compartido: tu maquina y GitHub Actions tienen que leer
  # y escribir el MISMO estado, o cada uno cree que la infraestructura no
  # existe y trata de recrearla de cero. Antes de este cambio, el estado
  # vivia solo en el disco local (terraform.tfstate, en .gitignore).
  #
  # "credentials" es obligatorio aca porque el backend NO reusa las
  # credenciales del bloque provider (son dos sistemas separados en
  # OpenTofu); sin esto, busca credenciales "por defecto" (ADC), que en
  # una maquina donde solo se corrio "gcloud auth login" -no "gcloud auth
  # application-default login"- no existen. Los bloques backend no
  # aceptan variables ni interpolacion, por eso la ruta va literal (es
  # la misma que ya usa el provider).
  backend "gcs" {
    bucket      = "sdypp-rocco-tofu-state"
    prefix      = "opentofu/state"
    credentials = "~/.gcp/opentofu-key.json"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}